package de.etiennebader.civ.application;

import de.etiennebader.civ.application.inmemory.InMemoryCommunityRepository;
import de.etiennebader.civ.application.inmemory.InMemoryContractRepository;
import de.etiennebader.civ.application.inmemory.InMemoryExchangeRepository;
import de.etiennebader.civ.application.inmemory.InMemoryPressRepository;
import de.etiennebader.civ.domain.community.CommunityRepository;
import de.etiennebader.civ.domain.contract.ContractRegistryRepository;
import de.etiennebader.civ.domain.economy.LedgerRepository;
import de.etiennebader.civ.domain.exchange.StockExchangeRepository;
import de.etiennebader.civ.domain.land.LandRepository;
import de.etiennebader.civ.domain.market.MarketRepository;
import de.etiennebader.civ.domain.media.PressRepository;
import de.etiennebader.civ.domain.polity.PolityRepository;
import de.etiennebader.civ.domain.shop.ShopRegistryRepository;
import de.etiennebader.civ.domain.territory.TerritoryRepository;

import java.util.Objects;

/**
 * Alle Repository-Ports, die die Use Cases benötigen.
 */
public record Repositories(PolityRepository polities, TerritoryRepository territory,
                           LandRepository land, LedgerRepository ledger, MarketRepository market,
                           ShopRegistryRepository shops, ContractRegistryRepository contracts,
                           CommunityRepository community, StockExchangeRepository exchange, PressRepository press) {

    /** Ohne Börse und Presse (im Speicher). */
    public Repositories(PolityRepository polities, TerritoryRepository territory, LandRepository land,
                        LedgerRepository ledger, MarketRepository market, ShopRegistryRepository shops,
                        ContractRegistryRepository contracts, CommunityRepository community) {
        this(polities, territory, land, ledger, market, shops, contracts, community,
                new InMemoryExchangeRepository(), new InMemoryPressRepository());
    }

    /** Ohne Verträge und Gemeinschaft (im Speicher). */
    public Repositories(PolityRepository polities, TerritoryRepository territory, LandRepository land,
                        LedgerRepository ledger, MarketRepository market, ShopRegistryRepository shops) {
        this(polities, territory, land, ledger, market, shops, new InMemoryContractRepository(),
                new InMemoryCommunityRepository());
    }

    public Repositories {
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(community, "community");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(press, "press");
        Objects.requireNonNull(polities, "polities");
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(land, "land");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(shops, "shops");
    }
}
