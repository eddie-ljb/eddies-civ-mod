package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.OrderId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Die Börse aller Firmen.
 *
 * <p>Orders sind Limit-Orders. Eine neue Order wird sofort gegen die Gegenseite ausgeführt, die besten Preise zuerst,
 * bei gleichem Preis die älteste; ausgeführt wird zum Preis der wartenden Order. Was übrig bleibt, wartet im Buch,
 * höchstens {@value #ORDER_DAYS} Tage. Eigene Orders werden nicht gegeneinander ausgeführt. Geld bucht der Aufrufer
 * über {@link Payments}; reicht es beim Käufer nicht, wird seine Order gestrichen.
 */
public final class StockExchange extends AggregateRoot<ExchangeEvent> implements StockExchangeView {

    /** So lange wartet eine Order im Buch. */
    public static final int ORDER_DAYS = 7;
    /** So viele offene Orders darf ein Akteur haben. */
    public static final int MAX_OPEN_ORDERS = 20;
    /** So viele Geschäfte bleiben gespeichert. */
    public static final int KEPT_TRADES = 300;
    /** So viele Tageskurse je Firma bleiben gespeichert. */
    public static final int KEPT_QUOTES = 90;
    /** So viele Dividenden bleiben gespeichert. */
    public static final int KEPT_DIVIDENDS = 100;

    private final Map<PolityId, Listing> listings = new LinkedHashMap<>();
    private final Map<PolityId, Map<ActorRef, Long>> holdings = new LinkedHashMap<>();
    private final Map<OrderId, Order> orders = new LinkedHashMap<>();
    private final List<Trade> trades = new ArrayList<>();
    private final Map<PolityId, List<Quote>> quotes = new LinkedHashMap<>();
    private final List<Dividend> dividends = new ArrayList<>();
    private final Map<PolityId, Long> lastPrice = new HashMap<>();
    private int lastNumber;

    private StockExchange() {
    }

    public static StockExchange empty() {
        return new StockExchange();
    }

    /**
     * @throws IllegalArgumentException wenn die Daten nicht zusammenpassen, z. B. mehr Aktien im Besitz als ausgegeben
     */
    public static StockExchange restore(Collection<Listing> listings, Collection<Holding> holdings,
                                        Collection<Order> orders, List<Trade> trades,
                                        Map<PolityId, List<Quote>> quotes, List<Dividend> dividends) {
        StockExchange exchange = new StockExchange();
        listings.forEach(listing -> exchange.listings.put(listing.company(), listing));
        for (Holding holding : holdings) {
            if (!exchange.listings.containsKey(holding.company())) {
                throw new IllegalArgumentException("Bestand einer nicht notierten Firma: " + holding.company());
            }
            exchange.holdings.computeIfAbsent(holding.company(), ignored -> new LinkedHashMap<>())
                    .merge(holding.holder(), holding.shares(), Long::sum);
        }
        for (Listing listing : listings) {
            long held = exchange.holdings.getOrDefault(listing.company(), Map.of()).values().stream()
                    .mapToLong(Long::longValue).sum();
            if (held != listing.totalShares()) {
                throw new IllegalArgumentException("Bestand passt nicht zur Aktienzahl: " + listing.company());
            }
        }
        orders.stream().sorted(Comparator.comparingInt(Order::number))
                .forEach(order -> exchange.orders.put(order.id(), order));
        exchange.trades.addAll(trades);
        quotes.forEach((company, list) -> exchange.quotes.put(company, new ArrayList<>(list)));
        exchange.dividends.addAll(dividends);
        exchange.lastNumber = orders.stream().mapToInt(Order::number).max().orElse(0);
        for (Trade trade : trades) {
            exchange.lastPrice.put(trade.company(), trade.price());
        }
        exchange.quotes.forEach((company, list) -> {
            if (!list.isEmpty() && !exchange.lastPrice.containsKey(company)) {
                exchange.lastPrice.put(company, list.getLast().close());
            }
        });
        return exchange;
    }

    /** Höchste je vergebene Ordernummer, damit Nummern nach dem Laden weiterzählen. */
    public int lastNumber() {
        return lastNumber;
    }

    /** Setzt den Zähler, z. B. nach dem Laden, wenn alle Orders erledigt waren. */
    public void continueNumbersAfter(int number) {
        lastNumber = Math.max(lastNumber, number);
    }

    // ---- Notierung ----

    /**
     * Bringt eine Firma an die Börse; der Gründer erhält alle Aktien.
     *
     * @throws IllegalArgumentException wenn sie schon notiert ist
     */
    public Listing list(PolityId company, ActorRef founder, long shares, long price, Day day) {
        Objects.requireNonNull(founder, "founder");
        if (listings.containsKey(company)) {
            throw new IllegalArgumentException("Schon notiert: " + company);
        }
        Listing listing = new Listing(company, shares, price, day);
        listings.put(company, listing);
        Map<ActorRef, Long> owners = new LinkedHashMap<>();
        owners.put(founder, shares);
        holdings.put(company, owners);
        quotes.put(company, new ArrayList<>(List.of(Quote.first(day, price, 0))));
        lastPrice.put(company, price);
        raise(new ExchangeEvent.CompanyListed(company, shares, price, day));
        return listing;
    }

    /** Neue Aktien, die zunächst der Firma selbst gehören; sie kann sie dann verkaufen. */
    public void issue(PolityId company, long shares, Day day) {
        Listing listing = requireListed(company);
        if (shares <= 0 || shares > 1_000_000) {
            throw new InvalidOrderException(shares);
        }
        listings.put(company, listing.withShares(listing.totalShares() + shares));
        holdings.get(company).merge(new PolityRef(company), shares, Long::sum);
        raise(new ExchangeEvent.SharesIssued(company, shares, day));
    }

    /**
     * Nimmt eine Firma von der Börse, z. B. bei Auflösung.
     *
     * @return die Aktionäre zuletzt, für die Verteilung des Restvermögens
     */
    public Map<ActorRef, Long> delist(PolityId company, Day day) {
        requireListed(company);
        Map<ActorRef, Long> owners = holders(company);
        listings.remove(company);
        holdings.remove(company);
        orders.values().removeIf(order -> order.company().equals(company));
        quotes.remove(company);
        lastPrice.remove(company);
        raise(new ExchangeEvent.CompanyDelisted(company, day));
        return owners;
    }

    // ---- Handel ----

    /** Ergebnis einer neuen Order. */
    public record Placement(Order order, List<Trade> trades, boolean resting, boolean stoppedForFunds) {

        public Placement {
            Objects.requireNonNull(order, "order");
            trades = List.copyOf(trades);
        }

        public long filled() {
            return trades.stream().mapToLong(Trade::quantity).sum();
        }
    }

    /**
     * Gibt eine Order auf und führt sie aus, so weit es geht.
     *
     * @throws NotListedException       wenn die Firma nicht notiert ist
     * @throws NotEnoughSharesException wenn ein Verkäufer nicht genug freie Aktien hat
     * @throws InvalidOrderException    bei unsinniger Menge, Preis oder zu vielen offenen Orders
     */
    public Placement place(OrderId id, PolityId company, ActorRef trader, Side side, long quantity, long limit,
                           Payments payments, Day day) {
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(payments, "payments");
        requireListed(company);
        if (quantity <= 0 || quantity > 1_000_000 || limit <= 0 || limit > 100_000_000) {
            throw new InvalidOrderException(quantity + " @ " + limit);
        }
        if (ordersOf(trader).size() >= MAX_OPEN_ORDERS) {
            throw new InvalidOrderException(MAX_OPEN_ORDERS);
        }
        if (side == Side.SELL && freeShares(company, trader) < quantity) {
            throw new NotEnoughSharesException(company);
        }
        Order order = new Order(id, ++lastNumber, company, trader, side, quantity, quantity, limit, day);
        raise(new ExchangeEvent.OrderPlaced(company, order.number(), side, day));
        List<Trade> done = new ArrayList<>();
        boolean stopped = false;
        for (Order resting : book(company, side == Side.BUY ? Side.SELL : Side.BUY)) {
            if (order.remaining() == 0) {
                break;
            }
            if (resting.trader().equals(trader)) {
                continue;
            }
            boolean crosses = side == Side.BUY ? resting.limit() <= order.limit() : resting.limit() >= order.limit();
            if (!crosses) {
                break;
            }
            ActorRef buyer = side == Side.BUY ? trader : resting.trader();
            ActorRef seller = side == Side.BUY ? resting.trader() : trader;
            long quantityNow = Math.min(order.remaining(), resting.remaining());
            if (sharesOf(company, seller) < quantityNow) {
                orders.remove(resting.id());
                continue;
            }
            long price = resting.limit();
            quantityNow = Math.min(quantityNow, Math.max(0, payments.affordable(buyer, price)));
            if (quantityNow == 0 || !payments.pay(buyer, seller, quantityNow * price)) {
                if (side == Side.BUY) {
                    stopped = true;
                    break;
                }
                orders.remove(resting.id());
                raise(new ExchangeEvent.OrderCancelled(company, resting.number(), day));
                continue;
            }
            transfer(company, seller, buyer, quantityNow);
            Trade trade = new Trade(company, buyer, seller, quantityNow, price, day);
            record(trade);
            done.add(trade);
            order = order.withRemaining(order.remaining() - quantityNow);
            if (side == Side.BUY && order.remaining() > 0 && quantityNow < resting.remaining()
                    && payments.affordable(buyer, price) == 0) {
                stopped = true;
            }
            long restingRest = resting.remaining() - quantityNow;
            boolean buyerExhausted = side == Side.SELL && quantityNow < Math.min(order.remaining() + quantityNow,
                    resting.remaining());
            if (restingRest == 0 || buyerExhausted) {
                orders.remove(resting.id());
            } else {
                orders.put(resting.id(), resting.withRemaining(restingRest));
            }
        }
        boolean resting = order.remaining() > 0 && !stopped;
        if (resting) {
            orders.put(order.id(), order);
        }
        return new Placement(order, done, resting, stopped);
    }

    /**
     * Streicht eine offene Order.
     *
     * @throws UnknownOrderException wenn es keine offene Order dieses Akteurs mit der Nummer gibt
     */
    public Order cancel(int number, ActorRef trader, Day day) {
        Order order = orderByNumber(number).filter(found -> found.trader().equals(trader))
                .orElseThrow(() -> new UnknownOrderException(number));
        orders.remove(order.id());
        raise(new ExchangeEvent.OrderCancelled(order.company(), number, day));
        return order;
    }

    /** Tagesende: alte Orders verfallen, Tage ohne Handel bekommen den letzten Kurs. */
    public List<Order> endOfDay(Day day) {
        List<Order> expired = new ArrayList<>();
        for (Order order : List.copyOf(orders.values())) {
            if (!order.placedOn().plus(ORDER_DAYS).isAfter(day)) {
                orders.remove(order.id());
                expired.add(order);
                raise(new ExchangeEvent.OrderCancelled(order.company(), order.number(), day));
            }
        }
        for (PolityId company : listings.keySet()) {
            List<Quote> list = quotes.computeIfAbsent(company, ignored -> new ArrayList<>());
            if (list.isEmpty() || list.getLast().day().isBefore(day)) {
                list.add(Quote.first(day, price(company), 0));
                trim(list, KEPT_QUOTES);
            }
        }
        return expired;
    }

    /** Hält eine Ausschüttung fest; das Geld bucht der Aufrufer. */
    public void recordDividend(Dividend dividend) {
        Objects.requireNonNull(dividend, "dividend");
        requireListed(dividend.company());
        dividends.add(dividend);
        trim(dividends, KEPT_DIVIDENDS);
        raise(new ExchangeEvent.DividendPaid(dividend.company(), dividend.perShare(), dividend.day()));
    }

    private void transfer(PolityId company, ActorRef from, ActorRef to, long quantity) {
        Map<ActorRef, Long> owners = holdings.get(company);
        long left = owners.get(from) - quantity;
        if (left == 0) {
            owners.remove(from);
        } else {
            owners.put(from, left);
        }
        owners.merge(to, quantity, Long::sum);
    }

    private void record(Trade trade) {
        trades.add(trade);
        trim(trades, KEPT_TRADES);
        lastPrice.put(trade.company(), trade.price());
        List<Quote> list = quotes.computeIfAbsent(trade.company(), ignored -> new ArrayList<>());
        if (!list.isEmpty() && list.getLast().day().equals(trade.day())) {
            list.set(list.size() - 1, list.getLast().with(trade.price(), trade.quantity()));
        } else {
            list.add(Quote.first(trade.day(), trade.price(), trade.quantity()));
            trim(list, KEPT_QUOTES);
        }
        raise(new ExchangeEvent.SharesTraded(trade.company(), trade.quantity(), trade.price(), trade.day()));
    }

    /** Wartende Orders einer Seite, beste zuerst: Kauf höchster, Verkauf niedrigster Preis; dann die älteste. */
    private List<Order> book(PolityId company, Side side) {
        Comparator<Order> byPrice = Comparator.comparingLong(Order::limit);
        if (side == Side.BUY) {
            byPrice = byPrice.reversed();
        }
        return orders.values().stream()
                .filter(order -> order.company().equals(company) && order.side() == side)
                .sorted(byPrice.thenComparingInt(Order::number))
                .toList();
    }

    private Listing requireListed(PolityId company) {
        Objects.requireNonNull(company, "company");
        Listing listing = listings.get(company);
        if (listing == null) {
            throw new NotListedException(company);
        }
        return listing;
    }

    private static <T> void trim(List<T> list, int keep) {
        while (list.size() > keep) {
            list.removeFirst();
        }
    }

    // ---- Lesen ----

    @Override
    public List<Listing> listings() {
        return List.copyOf(listings.values());
    }

    @Override
    public Optional<Listing> listing(PolityId company) {
        return Optional.ofNullable(listings.get(company));
    }

    @Override
    public Map<ActorRef, Long> holders(PolityId company) {
        Map<ActorRef, Long> sorted = new LinkedHashMap<>();
        holdings.getOrDefault(company, Map.of()).entrySet().stream()
                .sorted(Map.Entry.<ActorRef, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(ActorRef.CANONICAL_ORDER)))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    @Override
    public long sharesOf(PolityId company, ActorRef holder) {
        return holdings.getOrDefault(company, Map.of()).getOrDefault(holder, 0L);
    }

    @Override
    public long freeShares(PolityId company, ActorRef holder) {
        long reserved = orders.values().stream()
                .filter(order -> order.company().equals(company) && order.trader().equals(holder)
                        && order.side() == Side.SELL)
                .mapToLong(Order::remaining).sum();
        return sharesOf(company, holder) - reserved;
    }

    @Override
    public List<Holding> holdingsOf(ActorRef holder) {
        List<Holding> result = new ArrayList<>();
        holdings.forEach((company, owners) -> {
            Long shares = owners.get(holder);
            if (shares != null && shares > 0) {
                result.add(new Holding(company, holder, shares));
            }
        });
        return result;
    }

    @Override
    public List<Holding> allHoldings() {
        List<Holding> result = new ArrayList<>();
        holdings.forEach((company, owners) -> owners.forEach((holder, shares) ->
                result.add(new Holding(company, holder, shares))));
        return result;
    }

    @Override
    public long price(PolityId company) {
        Long last = lastPrice.get(company);
        if (last != null) {
            return last;
        }
        return Optional.ofNullable(listings.get(company)).map(Listing::initialPrice).orElse(0L);
    }

    @Override
    public List<Order> orders() {
        return List.copyOf(orders.values());
    }

    @Override
    public List<Order> orders(PolityId company) {
        return orders.values().stream().filter(order -> order.company().equals(company)).toList();
    }

    @Override
    public List<Order> ordersOf(ActorRef trader) {
        return orders.values().stream().filter(order -> order.trader().equals(trader)).toList();
    }

    @Override
    public Optional<Order> orderByNumber(int number) {
        return orders.values().stream().filter(order -> order.number() == number).findFirst();
    }

    @Override
    public List<Quote> quotes(PolityId company) {
        return List.copyOf(quotes.getOrDefault(company, List.of()));
    }

    @Override
    public Map<PolityId, List<Quote>> allQuotes() {
        Map<PolityId, List<Quote>> copy = new LinkedHashMap<>();
        quotes.forEach((company, list) -> copy.put(company, List.copyOf(list)));
        return copy;
    }

    @Override
    public List<Trade> trades() {
        return List.copyOf(trades);
    }

    @Override
    public List<Dividend> dividends() {
        return List.copyOf(dividends);
    }
}
