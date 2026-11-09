package de.etiennebader.civ.domain.market;

/**
 * Wie Lagerbestand und Preisniveau den Preis einer Ware bestimmen.
 *
 * <ul>
 *   <li>Stückpreis bei Bestand s: Grundpreis × Preisniveau × L / (L + s); bei {@code s = L} also der halbe Preis.
 *       Nie unter {@value #FLOOR} des Grundpreises.</li>
 *   <li>Wer verkauft, bekommt den Stückpreis abzüglich {@value #SPREAD} Spanne; wer kauft, zahlt die Spanne drauf.
 *       Dadurch lohnt sich Kaufen und sofortiges Wiederverkaufen nie.</li>
 *   <li>Der Bestand schrumpft jeden Tag um {@value #DAILY_DECAY}: Der Markt verbraucht Waren, Preise erholen sich.</li>
 * </ul>
 *
 * @param liquidity Bestand {@code L}, bei dem der Preis auf die Hälfte gefallen ist
 */
public record PriceModel(long liquidity) {

    public static final double SPREAD = 0.10;
    public static final double FLOOR = 0.05;
    public static final double DAILY_DECAY = 0.05;

    /** Vier Stapel Bestand halbieren den Preis. */
    public static final PriceModel STANDARD = new PriceModel(256);

    public PriceModel {
        if (liquidity < 1) {
            throw new IllegalArgumentException("liquidity muss mindestens 1 sein: " + liquidity);
        }
    }

    /** Stückpreis in kleinster Währungseinheit, ohne Spanne, als Kommazahl. */
    public double unitPrice(long basePrice, PriceLevel level, long stock) {
        double scarcity = (double) liquidity / (liquidity + Math.max(0, stock));
        return basePrice * level.factor() * Math.max(FLOOR, scarcity);
    }

    /** Erlös für {@code quantity} Stück, abgerundet; der Bestand steigt dabei Stück für Stück. */
    public long saleProceeds(long basePrice, PriceLevel level, long stock, int quantity) {
        double total = 0;
        for (int sold = 0; sold < quantity; sold++) {
            total += unitPrice(basePrice, level, stock + sold) * (1 - SPREAD);
        }
        return (long) Math.floor(total);
    }

    /** Kaufpreis für {@code quantity} Stück, aufgerundet; der Bestand sinkt dabei Stück für Stück. */
    public long purchaseCost(long basePrice, PriceLevel level, long stock, int quantity) {
        double total = 0;
        for (int bought = 1; bought <= quantity; bought++) {
            total += unitPrice(basePrice, level, stock - bought) * (1 + SPREAD);
        }
        return (long) Math.ceil(total);
    }

    /** Bestand nach {@code days} Tagen Verbrauch, abgerundet. */
    public long decayed(long stock, long days) {
        if (days <= 0 || stock <= 0) {
            return Math.max(0, stock);
        }
        return (long) Math.floor(stock * Math.pow(1 - DAILY_DECAY, days));
    }
}
