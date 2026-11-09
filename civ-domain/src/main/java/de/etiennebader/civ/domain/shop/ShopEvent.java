package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.territory.BlockPosition;

/**
 * Ereignisse der Läden.
 */
public sealed interface ShopEvent extends DomainEvent permits ShopOpened, ShopClosed, ShopSale {

    BlockPosition position();
}
