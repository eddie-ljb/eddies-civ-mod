package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.contract.ContractRegistry;
import de.etiennebader.civ.domain.contract.ContractStateException;
import de.etiennebader.civ.domain.contract.ContractStatus;
import de.etiennebader.civ.domain.contract.EndReason;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Verträge anbieten, unterschreiben, ablehnen und kündigen.
 *
 * <ul>
 *   <li><b>Miete:</b> Anbieter ist, wer über das Grundstück verfügt (Inhaber; bei Gemeindegrundstücken Bürgermeister
 *       oder Bauamt). Der Mieter zahlt täglich an den Inhaber und bei Unterschrift die Kaution.</li>
 *   <li><b>Arbeit:</b> Der Arbeitgeber bietet an und zahlt täglich Lohn. Ein Dorfbewohner nimmt sofort an, wenn er
 *       noch nicht arbeitet und der Lohn mindestens {@value #VILLAGER_MIN_WAGE} beträgt.</li>
 *   <li><b>Frei:</b> Text mit einmaliger und/oder täglicher Zahlung des Anbieters.</li>
 *   <li><b>Bündnis:</b> zwischen zwei Gemeinden oder Bünden; anbieten und unterschreiben darf jeweils die Spitze.</li>
 * </ul>
 * Kündigen kann jede Seite eines geltenden Vertrags mit {@value #NOTICE_DAYS} Tag Frist.
 */
public final class ContractUseCases {

    /** Mindestlohn, für den ein Dorfbewohner arbeitet: ein Viertel Smaragd am Tag. */
    public static final long VILLAGER_MIN_WAGE = 25;
    /** Kündigungsfrist in Tagen. */
    public static final long NOTICE_DAYS = 1;

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public ContractUseCases(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    private WorldQueries queries() {
        return new WorldQueries(aggregates.repositories());
    }

    /**
     * Bietet ein Grundstück zur Miete an.
     *
     * @throws PlotNotLeasableException wenn der Anbieter nicht darüber verfügt oder es schon vermietet ist
     */
    public Contract offerLease(ActorRef landlord, PolityId town, PlotId plotId, ActorRef tenant, Money perDay,
                               Money deposit, long termDays, Day day) {
        Polity polity = aggregates.polity(town);
        Plot plot = aggregates.land(town).plot(plotId).orElseThrow(PlotNotLeasableException::new);
        boolean holder = plot.holder().equals(landlord);
        boolean townPlot = plot.holder().equals(polity.asActor())
                && (polity.holds(landlord, Office.MAYOR) || polity.holds(landlord, Office.PLANNER));
        if (!holder && !townPlot || !queries().hasFreeUnit(queries().polity(town).orElseThrow(), plotId) || tenant.equals(plot.holder())) {
            throw new PlotNotLeasableException();
        }
        ActorRef offeror = townPlot ? polity.asActor() : landlord;
        String title = "Miete " + polity.name().value();
        return offer(new Contract(ContractId.generate(ids), 0, ContractKind.LEASE, offeror, tenant, tenant, offeror,
                perDay, Money.zero(perDay.currency()), deposit, Optional.of(new Contract.PlotRef(town, plotId)),
                title, "", termDays, day, Optional.empty(), Optional.empty(), ContractStatus.OFFERED,
                Optional.empty()), day);
    }

    /**
     * Bietet eine Stelle an. Ein Dorfbewohner entscheidet sofort.
     *
     * @throws VillagerDeclinesException wenn ein Dorfbewohner ablehnt
     */
    public Contract offerEmployment(ActorRef employer, ActorRef employee, Money wage, long termDays, Day day) {
        if (employee instanceof PersonRef) {
            boolean employed = queries().contracts().active().stream()
                    .anyMatch(contract -> contract.kind() == ContractKind.EMPLOYMENT && contract.payee().equals(employee));
            if (employed || wage.amount() < VILLAGER_MIN_WAGE) {
                throw new VillagerDeclinesException(VILLAGER_MIN_WAGE);
            }
        }
        Contract offered = offer(new Contract(ContractId.generate(ids), 0, ContractKind.EMPLOYMENT, employer, employee,
                employer, employee, wage, Money.zero(wage.currency()), Money.zero(wage.currency()), Optional.empty(),
                "Arbeit", "", termDays, day, Optional.empty(), Optional.empty(), ContractStatus.OFFERED,
                Optional.empty()), day);
        if (employee instanceof PersonRef) {
            return sign(offered.id(), employee, day);
        }
        return offered;
    }

    /** Bietet einen freien Vertrag an; der Anbieter zahlt die vereinbarten Beträge. */
    public Contract offerCustom(ActorRef offeror, ActorRef counterparty, Money once, Money perDay, String title,
                                String text, long termDays, Day day) {
        return offer(new Contract(ContractId.generate(ids), 0, ContractKind.CUSTOM, offeror, counterparty, offeror,
                counterparty, perDay, once, Money.zero(once.currency()), Optional.empty(), title, text, termDays, day,
                Optional.empty(), Optional.empty(), ContractStatus.OFFERED, Optional.empty()), day);
    }

    /**
     * Bietet ein Bündnis an. Nur die Spitze der anbietenden Polity darf das.
     *
     * @throws NotOfficeHolderException wenn der Akteur sie nicht führt
     */
    public Contract offerAlliance(ActorRef head, PolityId own, PolityId other, Day day) {
        Polity polity = aggregates.polity(own);
        requireHead(polity, head);
        Polity partner = aggregates.polity(other);
        String title = "Bündnis " + polity.name().value() + " – " + partner.name().value();
        Money zero = Money.zero(polity.currency());
        return offer(new Contract(ContractId.generate(ids), 0, ContractKind.ALLIANCE, polity.asActor(),
                partner.asActor(), polity.asActor(), partner.asActor(), zero, zero, zero, Optional.empty(), title, "",
                0, day, Optional.empty(), Optional.empty(), ContractStatus.OFFERED, Optional.empty()), day);
    }

    private Contract offer(Contract draft, Day day) {
        ContractRegistry registry = aggregates.contracts();
        Contract numbered = new Contract(draft.id(), registry.nextNumber(), draft.kind(), draft.offeror(),
                draft.counterparty(), draft.payer(), draft.payee(), draft.perDay(), draft.once(), draft.deposit(),
                draft.plot(), draft.title(), draft.text(), draft.termDays(), draft.offeredOn(), draft.startedOn(),
                draft.endsOn(), draft.status(), draft.endReason());
        registry.offer(numbered, day);
        aggregates.commit(registry);
        return numbered;
    }

    /**
     * Unterschreibt ein Angebot. Bei Polity-Verträgen unterschreibt deren Spitze für sie. Einmalige Zahlung und
     * Kaution gehen sofort; reicht das Geld nicht, bleibt das Angebot offen.
     *
     * @throws ContractStateException wenn der Akteur nicht unterschreiben darf
     * @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Geld nicht reicht
     */
    public Contract sign(ContractId id, ActorRef actor, Day day) {
        ContractRegistry registry = aggregates.contracts();
        Contract contract = registry.contract(id).orElseThrow(() -> new ContractStateException(0));
        ActorRef signer = signerFor(contract.counterparty(), actor, contract.number());
        if (contract.kind() == ContractKind.LEASE
                && !queries().hasFreeUnit(queries().polity(contract.plot().orElseThrow().polity()).orElseThrow(),
                contract.plot().orElseThrow().plot())) {
            throw new PlotNotLeasableException();
        }
        Ledger ledger = aggregates.ledger();
        if (contract.once().isPositive()) {
            ledger.transfer(contract.payer(), contract.payee(), contract.once(), TransferReason.TRADE, day);
        }
        if (contract.deposit().isPositive()) {
            ledger.transfer(contract.payer(), contract.payee(), contract.deposit(), TransferReason.DEPOSIT, day);
        }
        Contract signed = registry.sign(id, signer, day);
        aggregates.commit(ledger);
        aggregates.commit(registry);
        return signed;
    }

    /** Lehnt ein Angebot ab oder zieht es zurück. */
    public void decline(ContractId id, ActorRef actor, Day day) {
        ContractRegistry registry = aggregates.contracts();
        Contract contract = registry.contract(id).orElseThrow(() -> new ContractStateException(0));
        ActorRef party = contract.offeror().equals(actor) || contract.counterparty().equals(actor) ? actor
                : partyActingFor(contract, actor);
        registry.decline(id, party, day);
        aggregates.commit(registry);
    }

    /** Kündigt einen geltenden Vertrag. */
    public Contract giveNotice(ContractId id, ActorRef actor, Day day) {
        ContractRegistry registry = aggregates.contracts();
        Contract contract = registry.contract(id).orElseThrow(() -> new ContractStateException(0));
        ActorRef party = contract.involves(actor) ? actor : partyActingFor(contract, actor);
        Contract noticed = registry.giveNotice(id, party, NOTICE_DAYS, day);
        aggregates.commit(registry);
        return noticed;
    }

    /** Wer für die Partei handelt: sie selbst oder, bei Polities, ihre Spitze. */
    private ActorRef signerFor(ActorRef party, ActorRef actor, int number) {
        if (party.equals(actor)) {
            return party;
        }
        if (party instanceof PolityRef polity && aggregates.polity(polity.id()).holds(actor, Office.MAYOR)) {
            return party;
        }
        throw new ContractStateException(number);
    }

    private ActorRef partyActingFor(Contract contract, ActorRef actor) {
        for (ActorRef party : new ActorRef[]{contract.offeror(), contract.counterparty()}) {
            if (party instanceof PolityRef polity && aggregates.polity(polity.id()).holds(actor, Office.MAYOR)) {
                return party;
            }
        }
        throw new ContractStateException(contract.number());
    }

    private static void requireHead(PolityView polity, ActorRef actor) {
        if (!polity.holds(actor, Office.MAYOR)) {
            throw new NotOfficeHolderException(actor, Office.MAYOR);
        }
    }

    /** Beendet einen Vertrag, z. B. weil sein Gegenstand wegfiel. */
    public void voidContract(ContractId id, Day day) {
        ContractRegistry registry = aggregates.contracts();
        if (registry.contract(id).filter(Contract::active).isPresent()) {
            registry.end(id, EndReason.VOID, day);
            aggregates.commit(registry);
        }
    }

    /** Ob ein Akteur über einen Vertrag entscheiden darf: als Partei oder als Spitze einer Polity-Partei. */
    public boolean mayActFor(Contract contract, ActorRef actor) {
        if (contract.involves(actor)) {
            return true;
        }
        try {
            partyActingFor(contract, actor);
            return true;
        } catch (ContractStateException e) {
            return false;
        }
    }
}
