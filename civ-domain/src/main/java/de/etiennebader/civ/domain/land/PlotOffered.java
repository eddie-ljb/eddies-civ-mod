package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Der Inhaber bietet ein Grundstück zum Verkauf an.
 */
public record PlotOffered(PolityId polity, PlotId plot, ActorRef seller, Money price, Day occurredOn) implements LandEvent {

    public PlotOffered {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
