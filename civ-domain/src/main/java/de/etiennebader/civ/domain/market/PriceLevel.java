package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Das allgemeine Preisniveau: wächst die Geldmenge je Kopf über den Richtwert, steigen alle
 * Preise (Inflation); ist wenig Geld im Umlauf, sinken sie.
 *
 * <p>Faktor = Wurzel aus (Geldmenge je Kopf / Richtwert), begrenzt auf {@value #MIN} bis {@value #MAX}.
 * Die Wurzel dämpft: Doppelt so viel Geld je Kopf heißt etwa 41 % höhere Preise.
 */
public record PriceLevel(double factor) {

    public static final double MIN = 0.5;
    public static final double MAX = 4.0;
    public static final PriceLevel NEUTRAL = new PriceLevel(1.0);

    public PriceLevel {
        if (!(factor >= MIN && factor <= MAX)) {
            throw new IllegalArgumentException("Preisniveau außerhalb von " + MIN + ".." + MAX + ": " + factor);
        }
    }

    /**
     * @param supply     gesamte Geldmenge der Währung
     * @param population Zahl der Wirtschaftsteilnehmer, mindestens 1 wird angenommen
     * @param reference  Geldmenge je Kopf, bei der das Preisniveau neutral ist
     */
    public static PriceLevel of(Money supply, int population, Money reference) {
        Objects.requireNonNull(supply, "supply");
        Objects.requireNonNull(reference, "reference");
        if (!reference.isPositive()) {
            throw new IllegalArgumentException("Richtwert muss positiv sein: " + reference);
        }
        double perCapita = (double) Math.max(0, supply.amount()) / Math.max(1, population);
        double factor = Math.sqrt(perCapita / reference.amount());
        return new PriceLevel(Math.clamp(factor, MIN, MAX));
    }
}
