package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Gemeinde löst ein Grundstück auf; die Fläche wird wieder Gemeindeland.
 */
public final class DissolvePlot {

    public record Command(PolityId polity, PlotId plot, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public DissolvePlot(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        LandRegistry land = aggregates.land(aggregates.polity(command.polity()).id());
        land.dissolve(command.plot(), command.day());
        aggregates.commit(land);
    }
}
