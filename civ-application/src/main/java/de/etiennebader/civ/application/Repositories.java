package de.etiennebader.civ.application;

import de.etiennebader.civ.domain.economy.LedgerRepository;
import de.etiennebader.civ.domain.land.LandRepository;
import de.etiennebader.civ.domain.market.MarketRepository;
import de.etiennebader.civ.domain.polity.PolityRepository;
import de.etiennebader.civ.domain.shop.ShopRegistryRepository;
import de.etiennebader.civ.domain.territory.TerritoryRepository;

import java.util.Objects;

/**
 * Alle Repository-Ports, die die Use Cases benötigen.
 */
public record Repositories(PolityRepository polities, TerritoryRepository territory,
                           LandRepository land, LedgerRepository ledger, MarketRepository market,
                           ShopRegistryRepository shops) {

    public Repositories {
        Objects.requireNonNull(polities, "polities");
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(land, "land");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(shops, "shops");
    }
}
