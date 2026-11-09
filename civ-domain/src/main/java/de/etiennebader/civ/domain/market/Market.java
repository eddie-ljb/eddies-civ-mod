package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.InvalidValueException;
import de.etiennebader.civ.domain.shared.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Der Servermarkt einer Währung: führt den Lagerbestand jeder Ware und berechnet Erlöse und
 * Kaufpreise nach dem {@link PriceModel}. Grundpreise kennt der Markt nicht; sie kommen vom
 * Aufrufer, weil sie vom Spielinhalt abhängen.
 */
public final class Market extends AggregateRoot<MarketEvent> implements MarketView {

    private final CurrencyId currency;
    private final PriceModel model;
    private final Map<Commodity, CommodityStock> stocks = new TreeMap<>();

    private Market(CurrencyId currency, PriceModel model) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.model = Objects.requireNonNull(model, "model");
    }

    public static Market open(CurrencyId currency) {
        return new Market(currency, PriceModel.STANDARD);
    }

    /** Stellt gespeicherte Bestände wieder her, ohne Events auszulösen. */
    public static Market restore(CurrencyId currency, List<CommodityStock> stocks) {
        Market market = open(currency);
        for (CommodityStock stock : stocks) {
            if (market.stocks.put(stock.commodity(), stock) != null) {
                throw new IllegalArgumentException("Ware doppelt: " + stock.commodity());
            }
        }
        return market;
    }

    /**
     * Kauft dem Verkäufer Waren ab.
     *
     * @return der Erlös, den die Anwendung dem Verkäufer gutschreibt
     * @throws InvalidValueException mit {@link ErrorCode#WORTHLESS_GOODS}, wenn der Erlös unter einer Einheit läge
     */
    public Money sell(ActorRef seller, Commodity commodity, int quantity, long basePrice, PriceLevel level, Day today) {
        Objects.requireNonNull(seller, "seller");
        requireQuantity(quantity);
        long stock = stockOf(commodity, today);
        long proceeds = model.saleProceeds(requireBasePrice(basePrice), level, stock, quantity);
        if (proceeds <= 0) {
            throw new InvalidValueException(ErrorCode.WORTHLESS_GOODS, commodity, quantity);
        }
        stocks.put(commodity, new CommodityStock(commodity, stock + quantity, today));
        Money amount = Money.of(proceeds, currency);
        raise(new CommoditySold(seller, commodity, quantity, amount, today));
        return amount;
    }

    /**
     * Was ein Kauf kosten würde, ohne ihn auszuführen.
     *
     * @throws InvalidValueException mit {@link ErrorCode#OUT_OF_STOCK}, wenn der Bestand nicht reicht
     */
    public Money purchaseCost(Commodity commodity, int quantity, long basePrice, PriceLevel level, Day today) {
        requireQuantity(quantity);
        long stock = stockOf(commodity, today);
        if (stock < quantity) {
            throw new InvalidValueException(ErrorCode.OUT_OF_STOCK, commodity, stock);
        }
        return Money.of(model.purchaseCost(requireBasePrice(basePrice), level, stock, quantity), currency);
    }

    /**
     * Verkauft Waren aus dem Lager. Der Aufrufer muss den Kaufpreis ({@link #purchaseCost}) vorher abbuchen.
     *
     * @return der Kaufpreis
     */
    public Money buy(ActorRef buyer, Commodity commodity, int quantity, long basePrice, PriceLevel level, Day today) {
        Objects.requireNonNull(buyer, "buyer");
        Money cost = purchaseCost(commodity, quantity, basePrice, level, today);
        long stock = stockOf(commodity, today);
        stocks.put(commodity, new CommodityStock(commodity, stock - quantity, today));
        raise(new CommodityBought(buyer, commodity, quantity, cost, today));
        return cost;
    }

    @Override
    public CurrencyId currency() {
        return currency;
    }

    @Override
    public long stockOf(Commodity commodity, Day today) {
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(today, "today");
        CommodityStock stock = stocks.get(commodity);
        if (stock == null) {
            return 0;
        }
        return model.decayed(stock.quantity(), today.value() - stock.asOf().value());
    }

    @Override
    public Quote quote(Commodity commodity, long basePrice, PriceLevel level, Day today) {
        long stock = stockOf(commodity, today);
        long base = requireBasePrice(basePrice);
        Money sell = Money.of(model.saleProceeds(base, level, stock, 1), currency);
        Money buy = Money.of(stock > 0 ? model.purchaseCost(base, level, stock, 1) : 0, currency);
        return new Quote(commodity, sell, buy, stock);
    }

    @Override
    public List<CommodityStock> stocks() {
        return new ArrayList<>(stocks.values());
    }

    private static void requireQuantity(int quantity) {
        if (quantity < 1) {
            throw new InvalidValueException(ErrorCode.INVALID_QUANTITY, quantity);
        }
    }

    private static long requireBasePrice(long basePrice) {
        if (basePrice < 0) {
            throw new IllegalArgumentException("Grundpreis darf nicht negativ sein: " + basePrice);
        }
        return basePrice;
    }
}
