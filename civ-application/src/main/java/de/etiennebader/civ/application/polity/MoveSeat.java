package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.NotSovereignException;

import java.util.Objects;

/**
 * Ein Bürger verlegt den Sitz seiner Gemeinde an eine Stelle im eigenen Gebiet.
 */
public final class MoveSeat {

    public record Command(PolityId polity, ActorRef citizen, BlockPosition seat, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(seat, "seat");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public MoveSeat(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws NotACitizenException  wenn der Akteur kein Bürger ist
     * @throws NotSovereignException wenn die Stelle nicht im eigenen Gebiet liegt
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        AmendLaw.requireCitizen(polity, command.citizen());
        boolean own = aggregates.territory().sovereignOf(command.seat().chunk())
                .filter(polity.id()::equals).isPresent();
        if (!own) {
            throw new NotSovereignException(command.seat().chunk());
        }
        polity.moveSeat(command.seat(), command.day());
        aggregates.commit(polity);
    }
}
