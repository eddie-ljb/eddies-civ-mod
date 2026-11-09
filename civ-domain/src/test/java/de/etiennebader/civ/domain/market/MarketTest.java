package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.InvalidValueException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTest {

    private static final CurrencyId EMERALD = CurrencyId.EMERALD;
    private static final Commodity LOG = Commodity.of("minecraft:oak_log");
    private static final Day DAY = Day.of(1);

    private final PlayerRef alice = new TestActors(4).player();
    private final Market market = Market.open(EMERALD);

    @Test
    @DisplayName("Erster Verkauf zahlt Grundpreis abzüglich Spanne; der Bestand steigt")
    void firstSalePaysBasePriceMinusSpread() {
        Money proceeds = market.sell(alice, LOG, 1, 100, PriceLevel.NEUTRAL, DAY);

        assertEquals(Money.of(90, EMERALD), proceeds);
        assertEquals(1, market.stockOf(LOG, DAY));
        assertEquals(List.of(new CommoditySold(alice, LOG, 1, proceeds, DAY)), market.pullEvents());
    }

    @Test
    @DisplayName("Viel Angebot drückt den Preis: bei Bestand L halbiert er sich")
    void supplyLowersPrice() {
        market.sell(alice, LOG, 256, 100, PriceLevel.NEUTRAL, DAY);

        Quote quote = market.quote(LOG, 100, PriceLevel.NEUTRAL, DAY);

        assertEquals(Money.of(45, EMERALD), quote.sellPrice());
        assertEquals(Money.of(56, EMERALD), quote.buyPrice());
        assertEquals(256, quote.stock());
    }

    @Test
    @DisplayName("Kaufen und sofort Wiederverkaufen bringt nie Gewinn")
    void noArbitrage() {
        market.sell(alice, LOG, 64, 100, PriceLevel.NEUTRAL, DAY);

        Money cost = market.buy(alice, LOG, 10, 100, PriceLevel.NEUTRAL, DAY);
        Money proceeds = market.sell(alice, LOG, 10, 100, PriceLevel.NEUTRAL, DAY);

        assertTrue(cost.compareTo(proceeds) > 0, cost + " > " + proceeds);
    }

    @Test
    @DisplayName("Ohne Lagerbestand kann nichts gekauft werden")
    void cannotBuyWithoutStock() {
        market.sell(alice, LOG, 3, 100, PriceLevel.NEUTRAL, DAY);

        var exception = assertThrows(InvalidValueException.class,
                () -> market.buy(alice, LOG, 4, 100, PriceLevel.NEUTRAL, DAY));

        assertEquals(ErrorCode.OUT_OF_STOCK, exception.code());
        assertEquals(3, market.stockOf(LOG, DAY));
    }

    @Test
    @DisplayName("Der Bestand schrumpft täglich um 5 %, die Preise erholen sich")
    void stockDecaysDaily() {
        market.sell(alice, LOG, 1_000, 100, PriceLevel.NEUTRAL, DAY);

        assertEquals(950, market.stockOf(LOG, Day.of(2)));
        assertEquals(598, market.stockOf(LOG, Day.of(11)));
    }

    @Test
    @DisplayName("Wertlose Waren werden abgelehnt, statt für null verkauft zu werden")
    void rejectsWorthlessGoods() {
        var exception = assertThrows(InvalidValueException.class,
                () -> market.sell(alice, LOG, 1, 1, PriceLevel.NEUTRAL, DAY));

        assertEquals(ErrorCode.WORTHLESS_GOODS, exception.code());
        assertEquals(0, market.stockOf(LOG, DAY));
    }

    @Test
    @DisplayName("Inflation: mehr Geld je Kopf hebt alle Preise, gedämpft und begrenzt")
    void priceLevelFollowsMoneySupply() {
        Money reference = Money.of(5_000, EMERALD);

        assertEquals(1.0, PriceLevel.of(Money.of(50_000, EMERALD), 10, reference).factor(), 1e-9);
        assertEquals(2.0, PriceLevel.of(Money.of(200_000, EMERALD), 10, reference).factor(), 1e-9);
        assertEquals(PriceLevel.MAX, PriceLevel.of(Money.of(10_000_000, EMERALD), 1, reference).factor());
        assertEquals(PriceLevel.MIN, PriceLevel.of(Money.zero(EMERALD), 0, reference).factor());
        assertEquals(Money.of(180, EMERALD),
                market.quote(LOG, 100, new PriceLevel(2.0), DAY).sellPrice());
    }

    @Test
    @DisplayName("Gespeicherte Bestände werden ohne Events wiederhergestellt")
    void restoresStocks() {
        Market restored = Market.restore(EMERALD, List.of(new CommodityStock(LOG, 40, Day.of(3))));

        assertEquals(38, restored.stockOf(LOG, Day.of(4)));
        assertTrue(restored.pullEvents().isEmpty());
        assertThrows(InvalidValueException.class, () -> Commodity.of("Oak Log"));
    }
}
