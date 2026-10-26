package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Grundstück hat den Inhaber gewechselt.
 */
public record PlotTransferred(PolityId polity, PlotId plot, ActorRef from, ActorRef to, Day occurredOn) implements LandEvent {

    public PlotTransferred {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
