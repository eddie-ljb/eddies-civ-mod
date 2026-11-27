package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.justice.CourtStaff;
import de.etiennebader.civ.domain.police.PoliceRegistry;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Occupation;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.war.MilitaryRank;
import de.etiennebader.civ.domain.war.WarRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Welchen Beruf ein Dorfbewohner ausübt, abgeleitet aus dem, was die Zivilisation über ihn weiß: Ämter und Sitze, Gericht,
 * Garnison, Polizei, Bank, Firma und Zeitung, Anstellung. Daran richtet der Client seine Kleidung aus.
 *
 * <p>Hat jemand mehrere Rollen, gilt die mit dem höchsten Vorrang ({@link Occupation}). Wer keine hat, kommt nicht vor.
 * Eine Berechnung geht einmal über alle Gemeinden und Verträge; sie eignet sich für den Rundgang alle paar Sekunden.
 */
public final class VillagerOccupations {

    private final Map<PersonRef, Occupation> assigned = new HashMap<>();

    private VillagerOccupations() {
    }

    /** Berufe aller Dorfbewohner, die einen haben. */
    public static Map<PersonRef, Occupation> of(WorldQueries queries, PoliceRegistry police, WarRegistry war) {
        Objects.requireNonNull(queries, "queries");
        VillagerOccupations occupations = new VillagerOccupations();
        occupations.collect(queries, police, war);
        return Map.copyOf(occupations.assigned);
    }

    static VillagerOccupations empty() {
        return new VillagerOccupations();
    }

    Map<PersonRef, Occupation> assigned() {
        return assigned;
    }

    /** Merkt den Beruf vor, falls er Vorrang vor dem bisherigen hat. Nur Dorfbewohner zählen. */
    void claim(ActorRef actor, Occupation occupation) {
        if (actor instanceof PersonRef person && occupation.dressed()) {
            assigned.merge(person, occupation, (old, now) -> now.outranks(old) ? now : old);
        }
    }

    private void collect(WorldQueries queries, PoliceRegistry police, WarRegistry war) {
        Map<PolityId, PolityView> polities = new HashMap<>();
        for (PolityView polity : queries.polities()) {
            polities.put(polity.id(), polity);
            switch (polity.kind()) {
                case COMPANY -> polity.mayor(true).ifPresent(head -> claim(head, Occupation.ENTREPRENEUR));
                case NEWSPAPER -> polity.mayor(true).ifPresent(head -> claim(head, Occupation.JOURNALIST));
                default -> {
                    for (OfficeTerm term : polity.offices()) {
                        claim(term.holder(), Occupation.POLITICIAN);
                    }
                    polity.parliament().seats().forEach(seat -> claim(seat.member(), Occupation.POLITICIAN));
                }
            }
            police.forceOf(polity.id()).keySet().forEach(member -> claim(member, Occupation.POLICE));
        }
        war.garrisons().values().forEach(garrison -> garrison.forEach((member, rank) -> claim(member,
                rank == MilitaryRank.SOLDIER || rank == MilitaryRank.SCOUT ? Occupation.SOLDIER : Occupation.OFFICER)));
        for (CourtStaff staff : queries.justice().staff().values()) {
            staff.judges().forEach(judge -> claim(judge, Occupation.JUDGE));
            staff.prosecutors().forEach(prosecutor -> claim(prosecutor, Occupation.JUDGE));
        }
        for (ActorRef bank : queries.credit().banks()) {
            claim(bank, Occupation.BANKER);
            if (bank instanceof PolityRef firm && polities.get(firm.id()) != null) {
                polities.get(firm.id()).mayor(true).ifPresent(head -> claim(head, Occupation.BANKER));
            }
        }
        for (Contract contract : queries.contracts().active()) {
            if (contract.kind() == ContractKind.EMPLOYMENT && contract.offeror() instanceof PolityRef firm
                    && polities.get(firm.id()) != null && polities.get(firm.id()).kind() == PolityKind.COMPANY) {
                claim(contract.counterparty(), queries.production().enterpriseOf(firm.id())
                        .map(Enterprise::branch)
                        .map(branch -> Occupation.employeeOf(branch.id()))
                        .orElse(Occupation.WORKER));
            }
        }
    }
}
