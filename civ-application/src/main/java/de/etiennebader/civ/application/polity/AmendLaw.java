package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.List;
import java.util.Objects;

/**
 * Ein Bürger ändert eine einzelne Regelung seiner Gemeinde, z. B. „Fremde dürfen Türen benutzen“.
 *
 * <p>Ältere Gesetze, die nur diese eine Regelung enthielten, werden dabei aufgehoben, damit das
 * Gesetzbuch nicht mit überholten Einzelregelungen volläuft. Gesetze mit mehreren Regelungen
 * bleiben bestehen; das neue Gesetz hat als jüngstes Vorrang. In Phase 0 entscheidet ein Bürger
 * allein; Abstimmungen schieben sich später davor.
 */
public final class AmendLaw {

    public record Command(PolityId polity, ActorRef citizen, ParameterValue<?> value, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public AmendLaw(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @return die ID des neuen Gesetzes
     * @throws NotACitizenException wenn der Akteur kein Bürger dieser Gemeinde ist
     */
    public StatuteId execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        boolean citizen = polity.membershipOf(command.citizen())
                .map(membership -> membership.type() == MembershipType.CITIZEN)
                .orElse(false);
        if (!citizen) {
            throw new NotACitizenException(command.citizen(), polity.name().value());
        }
        for (EnactedStatute superseded : supersededBy(polity, command.value())) {
            polity.repeal(superseded.statute().id(), command.day());
        }
        Statute statute = Statute.of(StatuteId.generate(ids), command.value().key().id(), command.value());
        polity.enact(statute, command.day());
        aggregates.commit(polity);
        return statute.id();
    }

    private static List<EnactedStatute> supersededBy(Polity polity, ParameterValue<?> value) {
        return polity.laws().inForce().stream()
                .filter(enacted -> enacted.statute().values().size() == 1)
                .filter(enacted -> enacted.statute().values().getFirst().key().equals(value.key()))
                .toList();
    }
}
