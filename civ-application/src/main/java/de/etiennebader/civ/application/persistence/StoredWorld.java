package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.domain.community.Community;
import de.etiennebader.civ.domain.contract.ContractRegistry;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.market.Market;
import de.etiennebader.civ.domain.media.Press;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shop.ShopRegistry;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.List;
import java.util.Objects;

/**
 * Der vollständige gespeicherte Zustand: alle Aggregate, wie sie aus dem {@link StateStore} kommen,
 * dazu Spielernamen und Kalender.
 *
 * @param polities in Gründungsreihenfolge
 * @param land     ein Grundbuch je Polity
 */
public record StoredWorld(List<Polity> polities, TerritoryRegistry territory, List<LandRegistry> land, Ledger ledger,
                          Market market, ShopRegistry shops, List<KnownPlayer> players, CalendarState calendar,
                          ContractRegistry contracts, Community community, StockExchange exchange, Press press) {

    /** Ohne Börse und Presse. */
    public StoredWorld(List<Polity> polities, TerritoryRegistry territory, List<LandRegistry> land, Ledger ledger,
                       Market market, ShopRegistry shops, List<KnownPlayer> players, CalendarState calendar,
                       ContractRegistry contracts, Community community) {
        this(polities, territory, land, ledger, market, shops, players, calendar, contracts, community,
                StockExchange.empty(), Press.empty());
    }

    /** Ohne Verträge und Gemeinschaft. */
    public StoredWorld(List<Polity> polities, TerritoryRegistry territory, List<LandRegistry> land, Ledger ledger,
                       Market market, ShopRegistry shops, List<KnownPlayer> players, CalendarState calendar) {
        this(polities, territory, land, ledger, market, shops, players, calendar, ContractRegistry.empty(),
                Community.empty());
    }

    public StoredWorld {
        polities = List.copyOf(Objects.requireNonNull(polities, "polities"));
        Objects.requireNonNull(territory, "territory");
        land = List.copyOf(Objects.requireNonNull(land, "land"));
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(shops, "shops");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(community, "community");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(press, "press");
    }
}
