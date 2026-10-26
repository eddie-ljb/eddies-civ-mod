package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Grundstück im Grundbuch einer bestimmten Polity.
 */
public record PlotKey(PolityId polity, PlotId plot) {

    public PlotKey {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
    }
}
