package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Ein Laden wurde geschlossen.
 */
public record ShopClosed(BlockPosition position, Day occurredOn) implements ShopEvent {

    public ShopClosed {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
