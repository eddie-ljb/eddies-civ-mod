package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.shared.DomainEvent;

/**
 * Ereignisse des Servermarkts.
 */
public sealed interface MarketEvent extends DomainEvent permits CommoditySold, CommodityBought {

    Commodity commodity();
}
