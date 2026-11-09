package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Jemand hat Waren an den Servermarkt verkauft.
 */
public record CommoditySold(ActorRef seller, Commodity commodity, int quantity, Money proceeds, Day occurredOn)
        implements MarketEvent {

    public CommoditySold {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(proceeds, "proceeds");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
