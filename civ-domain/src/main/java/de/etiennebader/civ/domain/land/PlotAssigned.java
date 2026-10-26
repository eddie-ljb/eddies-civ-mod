package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Die Polity hat ein Grundstück an einen Akteur vergeben.
 */
public record PlotAssigned(PolityId polity, PlotId plot, ActorRef holder, Day occurredOn) implements LandEvent {

    public PlotAssigned {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
