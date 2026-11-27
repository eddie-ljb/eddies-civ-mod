package de.etiennebader.civ.domain.police;

/**
 * Kriminalität eines Dorfbewohners (0 bis 30, üblicherweise gering) aus Bildung, Ordnung/Freiheit, Stärke und
 * Zufriedenheit (hier: Heimatverbundenheit). Hohe Bildung, Ordnungssinn, Friedfertigkeit und Verbundenheit senken sie.
 */
public final class Criminality {

    public static final int MAX = 30;
    private static final int BASE = 4;

    private Criminality() {
    }

    /**
     * @param education Bildung 0 bis 100 (50 neutral)
     * @param order     Ordnung (+) gegen Freiheit (-), -100 bis 100
     * @param peace     Friedfertigkeit (+) gegen Stärke (-), -100 bis 100
     * @param belonging Zufriedenheit/Verbundenheit, -100 bis 100
     */
    public static int of(int education, int order, int peace, int belonging) {
        int value = BASE + (50 - education) / 10 - order / 20 - peace / 20 - belonging / 25;
        return Math.max(0, Math.min(MAX, value));
    }

    /** Wahrscheinlichkeit pro Tag (in Promille), dass ein Dorfbewohner mit diesem Wert eine Straftat begeht. */
    public static int dailyChancePerMille(int criminality) {
        return criminality / 3;
    }

    /** Die Art der Tat bei einem Zufallswert 0 bis 99; schwere Taten sind selten und brauchen hohe Kriminalität. */
    public static CrimeKind kindFor(int criminality, int roll) {
        if (criminality >= 20 && roll >= 97) {
            return CrimeKind.MURDER;
        }
        if (criminality >= 10 && roll >= 80) {
            return CrimeKind.ASSAULT;
        }
        return roll >= 40 ? CrimeKind.THEFT : CrimeKind.TRESPASS;
    }
}
