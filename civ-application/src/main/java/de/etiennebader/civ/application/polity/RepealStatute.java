package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.Objects;

/**
 * Hebt ein geltendes Gesetz auf.
 */
public final class RepealStatute {

    public record Command(PolityId polity, StatuteId statute, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(statute, "statute");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public RepealStatute(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        polity.repeal(command.statute(), command.day());
        aggregates.commit(polity);
    }
}
