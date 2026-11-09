package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Jemand hat Waren vom Servermarkt gekauft.
 */
public record CommodityBought(ActorRef buyer, Commodity commodity, int quantity, Money cost, Day occurredOn)
        implements MarketEvent {

    public CommodityBought {
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
