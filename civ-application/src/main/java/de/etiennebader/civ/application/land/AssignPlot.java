package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Gemeinde vergibt eines ihrer Grundstücke unentgeltlich, z. B. eine Botschaft an eine andere Gemeinde.
 * Für Verkäufe gibt es {@link SellPlot}.
 */
public final class AssignPlot {

    public record Command(PolityId polity, PlotId plot, ActorRef holder, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public AssignPlot(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        LandRegistry land = aggregates.land(aggregates.polity(command.polity()).id());
        land.assign(command.plot(), command.holder(), command.day());
        aggregates.commit(land);
    }
}
