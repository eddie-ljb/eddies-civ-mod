package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.domain.justice.JudiciaryView;
import de.etiennebader.civ.domain.justice.Judiciary;
import de.etiennebader.civ.domain.civic.CivicView;
import de.etiennebader.civ.domain.civic.CivicRegistry;
import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.community.ChatMessage;
import de.etiennebader.civ.domain.community.CommunityView;
import de.etiennebader.civ.domain.community.Letter;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.economy.CurrencyTotals;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.exchange.Holding;
import de.etiennebader.civ.domain.exchange.Listing;
import de.etiennebader.civ.domain.exchange.Order;
import de.etiennebader.civ.domain.exchange.Quote;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.exchange.Trade;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotSnapshot;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.CommodityStock;
import de.etiennebader.civ.domain.market.MarketView;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.polity.PolitySnapshot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shop.Shop;
import de.etiennebader.civ.domain.shop.ShopRegistryView;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Die zu schreibenden Werte eines {@link ChangeSet}: eine unveränderliche Kopie, die auf dem
 * Spielthread erfasst und danach in einem anderen Thread geschrieben werden kann.
 *
 * <p>Ein leerer Wert bedeutet „existiert nicht mehr“ und führt zum Löschen. Alle Listen behalten
 * die Reihenfolge der ersten Änderung.
 */
public record StateDelta(List<PolityChange> polities, List<ClaimChange> claims, List<PlotChange> plots,
                         List<BalanceChange> balances, List<CurrencyTotals> currencies, List<CommodityStock> commodities,
                         List<ShopChange> shops, List<DomainEvent> events, List<KnownPlayer> players, CalendarState calendar,
                         Optional<List<Contract>> contracts, Optional<CommunitySnapshot> community,
                         Optional<ExchangeSnapshot> exchange, Optional<List<Article>> press,
                         Optional<CivicRegistry> civic, Optional<Judiciary> justice) {

    /** Ohne Wahlkreise und Justiz. */
    public StateDelta(List<PolityChange> polities, List<ClaimChange> claims, List<PlotChange> plots,
                      List<BalanceChange> balances, List<CurrencyTotals> currencies, List<CommodityStock> commodities,
                      List<ShopChange> shops, List<DomainEvent> events, List<KnownPlayer> players,
                      CalendarState calendar, Optional<List<Contract>> contracts, Optional<CommunitySnapshot> community,
                      Optional<ExchangeSnapshot> exchange, Optional<List<Article>> press) {
        this(polities, claims, plots, balances, currencies, commodities, shops, events, players, calendar, contracts,
                community, exchange, press, Optional.empty(), Optional.empty());
    }

    /** Die ganze Börse. */
    public record ExchangeSnapshot(List<Listing> listings, List<Holding> holdings, List<Order> orders,
                                   List<Trade> trades, Map<PolityId, List<Quote>> quotes, List<Dividend> dividends) {

        public ExchangeSnapshot {
            listings = List.copyOf(listings);
            holdings = List.copyOf(holdings);
            orders = List.copyOf(orders);
            trades = List.copyOf(trades);
            quotes = Map.copyOf(quotes);
            dividends = List.copyOf(dividends);
        }

        public static ExchangeSnapshot of(StockExchangeView exchange) {
            return new ExchangeSnapshot(exchange.listings(), exchange.allHoldings(), exchange.orders(),
                    exchange.trades(), exchange.allQuotes(), exchange.dividends());
        }
    }

    /** Ohne Börse und Presse. */
    public StateDelta(List<PolityChange> polities, List<ClaimChange> claims, List<PlotChange> plots,
                      List<BalanceChange> balances, List<CurrencyTotals> currencies, List<CommodityStock> commodities,
                      List<ShopChange> shops, List<DomainEvent> events, List<KnownPlayer> players,
                      CalendarState calendar, Optional<List<Contract>> contracts,
                      Optional<CommunitySnapshot> community) {
        this(polities, claims, plots, balances, currencies, commodities, shops, events, players, calendar, contracts,
                community, Optional.empty(), Optional.empty());
    }

    /** Alle Briefe, Freundschaften, Anfragen und Chatnachrichten. */
    public record CommunitySnapshot(List<Letter> letters, List<List<PlayerRef>> friendships,
                                    List<List<PlayerRef>> requests, List<List<PlayerRef>> trusts,
                                    List<ChatMessage> messages) {

        public CommunitySnapshot {
            letters = List.copyOf(letters);
            friendships = List.copyOf(friendships);
            requests = List.copyOf(requests);
            trusts = List.copyOf(trusts);
            messages = List.copyOf(messages);
        }

        public static CommunitySnapshot of(CommunityView community) {
            return new CommunitySnapshot(community.letters(), community.friendships(), community.requests(),
                    community.trusts(), community.messages());
        }
    }

    /** Ohne Verträge und Gemeinschaft. */
    public StateDelta(List<PolityChange> polities, List<ClaimChange> claims, List<PlotChange> plots,
                      List<BalanceChange> balances, List<CurrencyTotals> currencies, List<CommodityStock> commodities,
                      List<ShopChange> shops, List<DomainEvent> events, List<KnownPlayer> players,
                      CalendarState calendar) {
        this(polities, claims, plots, balances, currencies, commodities, shops, events, players, calendar,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public StateDelta {
        polities = List.copyOf(Objects.requireNonNull(polities, "polities"));
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        plots = List.copyOf(Objects.requireNonNull(plots, "plots"));
        balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
        currencies = List.copyOf(Objects.requireNonNull(currencies, "currencies"));
        commodities = List.copyOf(Objects.requireNonNull(commodities, "commodities"));
        shops = List.copyOf(Objects.requireNonNull(shops, "shops"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        Objects.requireNonNull(calendar, "calendar");
        contracts = Objects.requireNonNull(contracts, "contracts").map(List::copyOf);
        Objects.requireNonNull(community, "community");
        Objects.requireNonNull(exchange, "exchange");
        press = Objects.requireNonNull(press, "press").map(List::copyOf);
        civic = Objects.requireNonNull(civic, "civic").map(StateDelta::copyOf);
        justice = Objects.requireNonNull(justice, "justice").map(StateDelta::copyOf);
    }

    /** Aktueller Stand einer geänderten Polity; leer, wenn sie aufgelöst wurde. */
    public record PolityChange(PolityId id, Optional<PolitySnapshot> polity) {

        public PolityChange {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(polity, "polity");
        }
    }

    /** Aktueller Claim eines geänderten Chunks; leer, wenn er freigegeben wurde. */
    public record ClaimChange(ChunkCoord chunk, Optional<Claim> claim) {

        public ClaimChange {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(claim, "claim");
        }
    }

    /** Aktueller Stand eines geänderten Grundstücks; leer, wenn es aufgelöst wurde. */
    public record PlotChange(PlotKey key, Optional<PlotSnapshot> plot) {

        public PlotChange {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(plot, "plot");
        }
    }

    /** Aktueller Laden an einer Kistenposition; leer, wenn er geschlossen wurde. */
    public record ShopChange(BlockPosition position, Optional<Shop> shop) {

        public ShopChange {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(shop, "shop");
        }
    }

    /** Neuer Kontostand; ein Stand von null bedeutet, das Konto wird nicht mehr geführt. */
    public record BalanceChange(AccountKey account, Money balance) {

        public BalanceChange {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(balance, "balance");
        }
    }

    /**
     * Liest die aktuellen Werte aller geänderten Einträge. Muss im Thread laufen, der den
     * Arbeitsstand verändert, damit die Kopie in sich stimmig ist.
     */
    public static StateDelta capture(ChangeSet changes, WorldQueries current, List<KnownPlayer> players,
                                     CalendarState calendar) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(current, "current");

        List<PolityChange> polities = new ArrayList<>();
        for (PolityId id : changes.polities()) {
            polities.add(new PolityChange(id, current.polity(id).map(PolitySnapshot::of)));
        }

        TerritoryView territory = current.territory();
        List<ClaimChange> claims = new ArrayList<>();
        for (ChunkCoord chunk : changes.chunks()) {
            claims.add(new ClaimChange(chunk, territory.claimAt(chunk)));
        }

        List<PlotChange> plots = new ArrayList<>();
        for (PlotKey key : changes.plots()) {
            Optional<PlotSnapshot> plot = current.polity(key.polity()).isPresent()
                    ? current.land(key.polity()).plot(key.plot()).map(Plot::snapshot)
                    : Optional.empty();
            plots.add(new PlotChange(key, plot));
        }

        LedgerView ledger = current.ledger();
        List<BalanceChange> balances = new ArrayList<>();
        for (AccountKey account : changes.accounts()) {
            balances.add(new BalanceChange(account, ledger.balanceOf(account.account(), account.currency())));
        }
        List<CurrencyTotals> currencies = new ArrayList<>();
        for (CurrencyId currency : changes.currencies()) {
            currencies.add(new CurrencyTotals(currency, ledger.totalSupply(currency), ledger.cashInCirculation(currency)));
        }

        MarketView market = current.market();
        List<CommodityStock> commodities = new ArrayList<>();
        Map<Commodity, CommodityStock> stored = new HashMap<>();
        market.stocks().forEach(stock -> stored.put(stock.commodity(), stock));
        for (Commodity commodity : changes.commodities()) {
            CommodityStock stock = stored.get(commodity);
            if (stock != null) {
                commodities.add(stock);
            }
        }

        ShopRegistryView shopRegistry = current.shops();
        List<ShopChange> shops = new ArrayList<>();
        for (BlockPosition position : changes.shops()) {
            shops.add(new ShopChange(position, shopRegistry.shopAt(position)));
        }

        Optional<List<Contract>> contracts = changes.contracts()
                ? Optional.of(current.contracts().contracts()) : Optional.empty();
        Optional<CommunitySnapshot> community = changes.community()
                ? Optional.of(CommunitySnapshot.of(current.community())) : Optional.empty();
        Optional<ExchangeSnapshot> exchange = changes.exchange()
                ? Optional.of(ExchangeSnapshot.of(current.exchange())) : Optional.empty();
        Optional<List<Article>> press = changes.press()
                ? Optional.of(current.press().articles()) : Optional.empty();
        Optional<CivicRegistry> civic = changes.civic() ? Optional.of(copyOf(current.civic())) : Optional.empty();
        Optional<Judiciary> justice = changes.justice() ? Optional.of(copyOf(current.justice())) : Optional.empty();
        return new StateDelta(polities, claims, plots, balances, currencies, commodities, shops, changes.events(),
                players, calendar, contracts, community, exchange, press, civic, justice);
    }

    /** Eine unabhängige Kopie, damit der Speicher-Thread keinen Arbeitsstand sieht, der sich noch ändert. */
    private static CivicRegistry copyOf(CivicView civic) {
        return CivicRegistry.restore(civic.constituencies(), civic.flags(), civic.anthems());
    }

    private static Judiciary copyOf(JudiciaryView justice) {
        return Judiciary.restore(justice.cases(), justice.staff(), justice.exiles());
    }
}
