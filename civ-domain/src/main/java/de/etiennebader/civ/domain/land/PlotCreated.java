package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Grundstück wurde angelegt; es gehört zunächst der Polity.
 */
public record PlotCreated(PolityId polity, PlotId plot, Area area, PlotType type, Day occurredOn) implements LandEvent {

    public PlotCreated {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
