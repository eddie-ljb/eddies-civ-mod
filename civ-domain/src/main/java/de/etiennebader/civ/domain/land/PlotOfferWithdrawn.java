package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Verkaufsangebot wurde zurückgezogen.
 */
public record PlotOfferWithdrawn(PolityId polity, PlotId plot, ActorRef holder, Day occurredOn) implements LandEvent {

    public PlotOfferWithdrawn {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
