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

    /** Dämpfung der Standardformel (Quadratwurzel): doppelte Geldmenge je Kopf heißt ~41 % höhere Preise. */
    public static final double DEFAULT_DAMPING = 0.5;

    /**
     * @param supply     gesamte Geldmenge der Währung
     * @param population Zahl der Wirtschaftsteilnehmer, mindestens 1 wird angenommen
     * @param reference  Geldmenge je Kopf, bei der das Preisniveau neutral ist
     */
    public static PriceLevel of(Money supply, int population, Money reference) {
        return of(supply, population, reference, MIN, MAX, DEFAULT_DAMPING);
    }

    /**
     * Wie {@link #of(Money, int, Money)}, aber mit einstellbaren Grenzen und Dämpfung, z. B. aus der
     * Server-Konfiguration ({@code price_level_min}, {@code price_level_max}, {@code price_level_damping}).
     *
     * @param minFactor kleinster Preisfaktor, mindestens {@value #MIN}
     * @param maxFactor größter Preisfaktor, höchstens {@value #MAX}
     * @param damping   Exponent auf das Verhältnis Geldmenge/Richtwert; 0,5 entspricht der Standardformel
     *                  (Quadratwurzel), 1,0 würde die Preise proportional zur Geldmenge je Kopf schwanken lassen
     */
    public static PriceLevel of(Money supply, int population, Money reference, double minFactor, double maxFactor,
                                 double damping) {
        Objects.requireNonNull(supply, "supply");
        Objects.requireNonNull(reference, "reference");
        if (!reference.isPositive()) {
            throw new IllegalArgumentException("Richtwert muss positiv sein: " + reference);
        }
        if (!(minFactor >= MIN && minFactor < maxFactor && maxFactor <= MAX)) {
            throw new IllegalArgumentException(
                    "Preisfaktor-Grenzen außerhalb von " + MIN + ".." + MAX + " oder min >= max: "
                            + minFactor + ".." + maxFactor);
        }
        if (!(damping > 0)) {
            throw new IllegalArgumentException("Dämpfung muss positiv sein: " + damping);
        }
        double perCapita = (double) Math.max(0, supply.amount()) / Math.max(1, population);
        double factor = Math.pow(perCapita / reference.amount(), damping);
        return new PriceLevel(Math.clamp(factor, minFactor, maxFactor));
    }
}
