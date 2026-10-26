package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Mitglied tritt aus einer Gemeinde aus. Seine Grundstücke behält es; sie bleiben grundsteuerpflichtig.
 */
public final class LeavePolity {

    public record Command(PolityId polity, ActorRef member, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public LeavePolity(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        polity.leave(command.member(), command.day());
        aggregates.commit(polity);
    }
}
