package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.contract.ContractRegistry;
import de.etiennebader.civ.domain.contract.EndReason;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tagesende für Verträge und Gehälter.
 *
 * <ul>
 *   <li>Ist der letzte Tag eines Vertrags erreicht, endet er; die Kaution geht zurück, wenn der Empfänger sie zahlen
 *       kann.</li>
 *   <li>Sonst zahlt der Zahler die tägliche Rate. Kann er nicht, ist der Vertrag gebrochen und endet; die Kaution
 *       verfällt.</li>
 *   <li>Ein Mietvertrag endet, wenn das Grundstück aufgelöst wurde oder den Inhaber gewechselt hat.</li>
 *   <li>Jede Polity mit {@link StandardParameters#OFFICE_SALARY} zahlt allen Amtsinhabern und Abgeordneten ihr Gehalt,
 *       solange die Kasse reicht.</li>
 * </ul>
 */
public final class RunContracts {

    /** Was am Tagesende geschah. */
    public sealed interface Outcome permits Ended, Paid, SalariesPaid {
    }

    public record Ended(Contract contract, EndReason reason) implements Outcome {
    }

    public record Paid(Contract contract, Money amount) implements Outcome {
    }

    public record SalariesPaid(PolityId polity, int recipients, Money total, int unpaid) implements Outcome {
    }

    private final Aggregates aggregates;

    public RunContracts(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public List<Outcome> execute(Day day) {
        Objects.requireNonNull(day, "day");
        List<Outcome> outcomes = new ArrayList<>();
        ContractRegistry registry = aggregates.contracts();
        Ledger ledger = aggregates.ledger();
        for (Contract contract : registry.active()) {
            Optional<EndReason> end = endReason(contract, day);
            if (end.isPresent()) {
                if (end.get() != EndReason.BREACH && contract.deposit().isPositive()) {
                    tryTransfer(ledger, contract.payee(), contract.payer(), contract.deposit(), TransferReason.DEPOSIT,
                            day);
                }
                outcomes.add(new Ended(registry.end(contract.id(), end.get(), day), end.get()));
                continue;
            }
            if (!contract.perDay().isPositive()) {
                continue;
            }
            if (tryTransfer(ledger, contract.payer(), contract.payee(), contract.perDay(), reasonOf(contract), day)) {
                outcomes.add(new Paid(contract, contract.perDay()));
            } else {
                outcomes.add(new Ended(registry.end(contract.id(), EndReason.BREACH, day), EndReason.BREACH));
            }
        }
        paySalaries(ledger, day, outcomes);
        aggregates.commit(ledger);
        aggregates.commit(registry);
        return outcomes;
    }

    private Optional<EndReason> endReason(Contract contract, Day day) {
        if (contract.kind() == ContractKind.LEASE) {
            Contract.PlotRef ref = contract.plot().orElseThrow();
            boolean polityExists = aggregates.repositories().polities().findById(ref.polity()).isPresent();
            Optional<Plot> plot = polityExists ? aggregates.land(ref.polity()).plot(ref.plot()) : Optional.empty();
            if (plot.isEmpty() || !plot.get().holder().equals(contract.payee())) {
                return Optional.of(EndReason.VOID);
            }
        }
        if (contract.endsOn().isPresent() && !day.isBefore(contract.endsOn().get())) {
            boolean expired = contract.termDays() > 0 && contract.startedOn()
                    .map(start -> start.plus(contract.termDays()).equals(contract.endsOn().get())).orElse(false);
            return Optional.of(expired ? EndReason.EXPIRED : EndReason.NOTICE);
        }
        return Optional.empty();
    }

    private static TransferReason reasonOf(Contract contract) {
        return switch (contract.kind()) {
            case LEASE -> TransferReason.RENT;
            case EMPLOYMENT -> TransferReason.WAGE;
            case CUSTOM, ALLIANCE -> TransferReason.TRADE;
        };
    }

    private void paySalaries(Ledger ledger, Day day, List<Outcome> outcomes) {
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            long salary = polity.laws().valueOf(StandardParameters.OFFICE_SALARY);
            if (salary <= 0) {
                continue;
            }
            Set<ActorRef> recipients = new LinkedHashSet<>();
            polity.mayor().ifPresent(recipients::add);
            polity.offices().stream().map(OfficeTerm::holder).forEach(recipients::add);
            polity.parliament().seats().stream().map(Parliament.Seat::member).forEach(recipients::add);
            Money each = Money.of(salary, polity.currency());
            int paid = 0;
            int unpaid = 0;
            for (ActorRef recipient : recipients) {
                if (tryTransfer(ledger, polity.asActor(), recipient, each, TransferReason.SALARY, day)) {
                    paid++;
                } else {
                    unpaid++;
                }
            }
            if (!recipients.isEmpty()) {
                outcomes.add(new SalariesPaid(polity.id(), paid, Money.of(salary * paid, polity.currency()), unpaid));
            }
        }
    }

    private static boolean tryTransfer(Ledger ledger, ActorRef from, ActorRef to, Money amount, TransferReason reason,
                                       Day day) {
        try {
            ledger.transfer(from, to, amount, reason, day);
            return true;
        } catch (InsufficientFundsException e) {
            return false;
        }
    }
}
