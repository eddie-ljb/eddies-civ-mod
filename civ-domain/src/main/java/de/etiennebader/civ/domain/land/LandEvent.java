package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/**
 * Alle Events des {@link LandRegistry}-Aggregats.
 */
public sealed interface LandEvent extends DomainEvent permits PlotCreated, PlotAssigned, PlotTransferred, PlotDissolved {

    /** Die Polity, deren Grundbuch sich geändert hat. */
    PolityId polity();

    PlotId plot();
}
