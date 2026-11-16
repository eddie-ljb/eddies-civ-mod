package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.OrderId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockExchangeTest {

    private static final Day DAY = Day.of(1);

    private final TestActors actors = new TestActors();
    private final PlayerRef founder = actors.player();
    private final PlayerRef alice = actors.player();
    private final PlayerRef bob = actors.player();
    private final PolityId company = actors.polityId();
    private final StockExchange exchange = StockExchange.empty();
    private final Map<ActorRef, Long> cash = new HashMap<>();

    private final Payments wallet = new Payments() {
        @Override
        public boolean pay(ActorRef from, ActorRef to, long units) {
            if (cash.getOrDefault(from, 0L) < units) {
                return false;
            }
            cash.merge(from, -units, Long::sum);
            cash.merge(to, units, Long::sum);
            return true;
        }

        @Override
        public long affordable(ActorRef payer, long price) {
            return cash.getOrDefault(payer, 0L) / price;
        }
    };

    private StockExchange.Placement place(ActorRef trader, Side side, long quantity, long limit) {
        return exchange.place(OrderId.generate(actors.ids()), company, trader, side, quantity, limit, wallet, DAY);
    }

    @Test
    @DisplayName("Ein Kauf trifft das günstigste Angebot zuerst, zum Preis der wartenden Order")
    void matchesBestPriceFirst() {
        exchange.list(company, founder, 1000, 100, DAY);
        place(founder, Side.SELL, 10, 120);
        place(founder, Side.SELL, 10, 110);
        assertEquals(980L, exchange.freeShares(company, founder));
        cash.put(alice, 5_000L);

        StockExchange.Placement placement = place(alice, Side.BUY, 15, 125);
        assertEquals(2, placement.trades().size());
        assertEquals(110L, placement.trades().get(0).price());
        assertEquals(10L, placement.trades().get(0).quantity());
        assertEquals(120L, placement.trades().get(1).price());
        assertEquals(15L, exchange.sharesOf(company, alice));
        assertEquals(5_000L - 10 * 110 - 5 * 120, cash.get(alice));
        assertEquals(120L, exchange.price(company));
        assertFalse(placement.resting());
        assertEquals(5L, exchange.orders(company).getFirst().remaining());
        Quote today = exchange.quotes(company).getLast();
        assertEquals(15L, today.volume());
        assertEquals(120L, today.high());
    }

    @Test
    @DisplayName("Ohne Gegenseite wartet die Order; ohne Geld wird der Kauf abgebrochen")
    void restsAndStopsForFunds() {
        exchange.list(company, founder, 1000, 100, DAY);
        cash.put(alice, 150L);
        StockExchange.Placement resting = place(alice, Side.BUY, 5, 90);
        assertTrue(resting.resting());
        place(founder, Side.SELL, 3, 90);
        assertEquals(1L, exchange.sharesOf(company, alice), "nur eine Aktie bezahlbar, dann wird die Kauforder gestrichen");
        assertTrue(exchange.ordersOf(alice).isEmpty());
        assertEquals(2L, exchange.orders(company).getFirst().remaining(), "der Rest des Verkaufs wartet");

        cash.put(bob, 50L);
        StockExchange.Placement stopped = place(bob, Side.BUY, 5, 100);
        assertTrue(stopped.stoppedForFunds());
        assertTrue(exchange.ordersOf(bob).isEmpty());
    }

    @Test
    @DisplayName("Verkaufen geht nur mit freien Aktien; eigene Orders laufen nicht gegeneinander")
    void sellRules() {
        exchange.list(company, founder, 100, 10, DAY);
        assertDomainError(ErrorCode.NOT_ENOUGH_SHARES, () -> place(alice, Side.SELL, 1, 10));
        place(founder, Side.SELL, 60, 10);
        assertDomainError(ErrorCode.NOT_ENOUGH_SHARES, () -> place(founder, Side.SELL, 50, 10));
        cash.put(founder, 1_000L);
        assertTrue(place(founder, Side.BUY, 5, 20).trades().isEmpty());
        assertDomainError(ErrorCode.NOT_LISTED, () -> exchange.place(OrderId.generate(actors.ids()), actors.polityId(),
                founder, Side.BUY, 1, 1, wallet, DAY));
        assertDomainError(ErrorCode.INVALID_ORDER, () -> place(founder, Side.BUY, 0, 1));
    }

    @Test
    @DisplayName("Streichen, Verfall nach 7 Tagen, Tageskurse auch ohne Handel")
    void cancelAndExpire() {
        exchange.list(company, founder, 100, 10, DAY);
        int number = place(founder, Side.SELL, 5, 12).order().number();
        assertDomainError(ErrorCode.UNKNOWN_ORDER, () -> exchange.cancel(number, alice, DAY));
        exchange.cancel(number, founder, DAY);
        assertTrue(exchange.orders().isEmpty());
        place(founder, Side.SELL, 5, 12);
        assertTrue(exchange.endOfDay(Day.of(7)).isEmpty());
        assertEquals(1, exchange.endOfDay(Day.of(8)).size());
        assertEquals(Day.of(8), exchange.quotes(company).getLast().day());
    }

    @Test
    @DisplayName("Neue Aktien gehören zuerst der Firma; Auflösung gibt die Aktionäre zurück")
    void issueAndDelist() {
        exchange.list(company, founder, 100, 10, DAY);
        exchange.issue(company, 50, DAY);
        assertEquals(150L, exchange.listing(company).orElseThrow().totalShares());
        assertEquals(50L, exchange.sharesOf(company, new PolityRef(company)));
        Map<ActorRef, Long> holders = exchange.delist(company, DAY);
        assertEquals(Map.of(founder, 100L, new PolityRef(company), 50L), holders);
        assertTrue(exchange.listings().isEmpty());
    }

    @Test
    @DisplayName("Wiederherstellen prüft, dass die Bestände zur Aktienzahl passen")
    void restore() {
        exchange.list(company, founder, 100, 10, DAY);
        cash.put(alice, 1_000L);
        place(founder, Side.SELL, 10, 11);
        place(alice, Side.BUY, 4, 11);
        exchange.recordDividend(new Dividend(company, 1, 100, DAY));
        StockExchange copy = StockExchange.restore(exchange.listings(), exchange.allHoldings(), exchange.orders(),
                exchange.trades(), exchange.allQuotes(), exchange.dividends());
        assertEquals(exchange.holders(company), copy.holders(company));
        assertEquals(11L, copy.price(company));
        assertEquals(6L, copy.orders().getFirst().remaining());
        assertThrows(IllegalArgumentException.class, () -> StockExchange.restore(exchange.listings(),
                List.of(new Holding(company, founder, 1)), List.of(), List.of(), Map.of(), List.of()));
    }
}
