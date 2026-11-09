package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Ein Laden an einer Kiste.
 *
 * @param price Preis je Stück
 */
public record Shop(BlockPosition position, ActorRef owner, Commodity commodity, Money price, Day openedOn) {

    public Shop {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(openedOn, "openedOn");
        if (!price.isPositive()) {
            throw new IllegalArgumentException("Preis muss positiv sein: " + price);
        }
    }
}
