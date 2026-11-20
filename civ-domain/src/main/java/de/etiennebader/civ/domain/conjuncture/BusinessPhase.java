package de.etiennebader.civ.domain.conjuncture;

/**
 * Die vier Phasen eines Konjunkturzyklus, in der Reihenfolge, in der eine Wirtschaftszone sie durchläuft.
 *
 * <p>{@link #baseMultiplier()} ist der rohe Effekt der Phase auf Produktion, Dorfbewohner-Einkommen und
 * Aktienkurse, bevor Kopplung an die globale Stimmung und Dämpfung durch Regierungsmaßnahmen
 * ({@link BusinessCycle#effectiveMultiplier}) greifen.
 */
public enum BusinessPhase {

    /** Aufschwung: wachsende Produktion und Nachfrage. */
    EXPANSION(1.10),
    /** Hochkonjunktur: der Zyklus auf seinem Höhepunkt. */
    PEAK(1.25),
    /** Abschwung: schrumpfende Produktion und Nachfrage. */
    CONTRACTION(0.90),
    /** Rezession: der Tiefpunkt des Zyklus. */
    TROUGH(0.70);

    private final double baseMultiplier;

    BusinessPhase(double baseMultiplier) {
        this.baseMultiplier = baseMultiplier;
    }

    /** Roher Multiplikator dieser Phase, ohne Kopplung oder Dämpfung. */
    public double baseMultiplier() {
        return baseMultiplier;
    }
}
