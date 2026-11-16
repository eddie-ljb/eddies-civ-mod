package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.exchange.Holding;
import de.etiennebader.civ.domain.exchange.Listing;
import de.etiennebader.civ.domain.exchange.Order;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dorfbewohner legen ihr Erspartes nach ihrer Persönlichkeit an.
 *
 * <ul>
 *   <li><b>Sparsame</b> (Sparsamkeit über 0) kaufen mit einem Teil ihres Geldes die Aktie mit dem besten Verhältnis
 *       aus Buchwert, Dividende und Kurs. Ordnungsliebende meiden schwankende Kurse, Freiheitsliebende mögen
 *       steigende.</li>
 *   <li><b>Ausgabefreudige</b> (Sparsamkeit unter -30) verkaufen nach und nach.</li>
 *   <li>Steht der Kurs weit über dem Buchwert, nehmen weniger Ordnungsliebende Gewinne mit; fällt er mehrere Tage,
 *       verkaufen Ordnungsliebende.</li>
 * </ul>
 *
 * <p>Je Tag kommen höchstens {@value #PER_DAY} Dorfbewohner an die Reihe, reihum.
 */
public final class VillagerInvestors {

    public static final int PER_DAY = 200;
    /** Unter diesem Kontostand kauft niemand: 2 Smaragde. */
    public static final long MIN_CASH = 200;

    /** Was an einem Tag gehandelt wurde. */
    public record Report(int buyOrders, int sellOrders, long sharesTraded) {
    }

    private final Supplier<WorldQueries> queries;
    private final Trading trading;

    public VillagerInvestors(Supplier<WorldQueries> queries, Trading trading) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.trading = Objects.requireNonNull(trading, "trading");
    }

    public Report execute(Day day) {
        WorldQueries world = queries.get();
        StockExchangeView exchange = world.exchange();
        if (exchange.listings().isEmpty()) {
            return new Report(0, 0, 0);
        }
        Set<PersonRef> all = new LinkedHashSet<>();
        for (PolityView polity : world.polities()) {
            if (polity.kind() == PolityKind.TERRITORIAL) {
                polity.members().stream().map(Membership::member).filter(PersonRef.class::isInstance)
                        .map(PersonRef.class::cast).forEach(all::add);
            }
        }
        List<PersonRef> villagers = new ArrayList<>(all);
        villagers.sort(Comparator.comparing(PersonRef::uuid));
        int count = Math.min(PER_DAY, villagers.size());
        int start = villagers.isEmpty() ? 0 : (int) Math.floorMod(day.value() * PER_DAY, (long) villagers.size());
        int buys = 0;
        int sells = 0;
        long traded = 0;
        for (int index = 0; index < count; index++) {
            PersonRef villager = villagers.get((start + index) % villagers.size());
            Personality personality = Personality.of(villager);
            WorldQueries now = queries.get();
            StockExchangeView current = now.exchange();
            for (Holding holding : current.holdingsOf(villager)) {
                Optional<Long> toSell = sellDecision(personality, holding, current, now, day);
                if (toSell.isPresent() && toSell.get() > 0 && !hasOrder(current, villager, holding.company())) {
                    long price = Math.max(1, current.price(holding.company()) * 97 / 100);
                    traded += place(villager, holding.company(), Side.SELL, toSell.get(), price, day);
                    sells++;
                }
            }
            if (personality.thrift() <= 0) {
                continue;
            }
            Optional<PolityId> pick = best(personality, current, now, day);
            if (pick.isEmpty() || hasOrder(current, villager, pick.get())) {
                continue;
            }
            long cash = now.polity(pick.get())
                    .map(company -> now.ledger().balanceOf(villager, company.currency()).amount()).orElse(0L);
            if (cash < MIN_CASH) {
                continue;
            }
            long price = Math.max(1, current.price(pick.get()) * 102 / 100);
            long budget = cash * personality.thrift() / 200;
            long shares = budget / price;
            if (shares > 0) {
                traded += place(villager, pick.get(), Side.BUY, shares, price, day);
                buys++;
            }
        }
        return new Report(buys, sells, traded);
    }

    private long place(PersonRef villager, PolityId company, Side side, long shares, long price, Day day) {
        try {
            return trading.place(villager, company, side, shares, price, day).filled();
        } catch (DomainException e) {
            return 0;
        }
    }

    private static boolean hasOrder(StockExchangeView exchange, ActorRef villager, PolityId company) {
        return exchange.ordersOf(villager).stream().map(Order::company).anyMatch(company::equals);
    }

    private Optional<Long> sellDecision(Personality personality, Holding holding, StockExchangeView exchange,
                                        WorldQueries world, Day day) {
        Optional<CompanyFigures> figures = figures(holding.company(), exchange, world, day);
        if (figures.isEmpty()) {
            return Optional.empty();
        }
        long shares = exchange.freeShares(holding.company(), holding.holder());
        if (personality.thrift() < -30) {
            return Optional.of(Math.max(1, shares / 2));
        }
        CompanyFigures f = figures.get();
        if (f.bookValue() > 0 && f.price() > f.bookValue() * 16 / 10 && personality.order() < 30) {
            return Optional.of(Math.max(1, shares / 3));
        }
        if (personality.order() > 40 && f.momentum() < -15) {
            return Optional.of(shares);
        }
        return Optional.empty();
    }

    private Optional<PolityId> best(Personality personality, StockExchangeView exchange, WorldQueries world, Day day) {
        PolityId best = null;
        double bestScore = 0.8;
        for (Listing listing : exchange.listings()) {
            Optional<CompanyFigures> figures = figures(listing.company(), exchange, world, day);
            if (figures.isEmpty() || figures.get().price() <= 0) {
                continue;
            }
            CompanyFigures f = figures.get();
            double score = (double) f.bookValue() / f.price() + f.dividendYield() / 5.0;
            if (personality.order() < 0) {
                score += Math.max(-0.5, Math.min(0.5, f.momentum() / 40.0));
            } else {
                score -= Math.min(0.5, Math.abs(f.momentum()) / 60.0);
            }
            if (score > bestScore) {
                bestScore = score;
                best = listing.company();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<CompanyFigures> figures(PolityId company, StockExchangeView exchange, WorldQueries world,
                                                    Day day) {
        Optional<Listing> listing = exchange.listing(company);
        Optional<PolityView> polity = world.polity(company);
        if (listing.isEmpty() || polity.isEmpty()) {
            return Optional.empty();
        }
        long treasury = world.ledger().balanceOf(polity.get().asActor(), polity.get().currency()).amount();
        return Optional.of(CompanyFigures.of(polity.get(), listing.get(), exchange, treasury, day));
    }
}
