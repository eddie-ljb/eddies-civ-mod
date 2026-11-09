package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Ein Laden wurde eröffnet oder sein Angebot geändert.
 */
public record ShopOpened(BlockPosition position, ActorRef owner, Commodity commodity, Money price, Day occurredOn)
        implements ShopEvent {

    public ShopOpened {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
