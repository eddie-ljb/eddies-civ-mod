package de.etiennebader.civ.sim.agent;

/**
 * Die veränderlichen Bedürfnisse eines Akteurs, jeweils zwischen 0 (erfüllt) und 1 (dringend).
 */
public final class Needs {

    private double belonging;
    private double housing;
    private double security;

    /** Zugehörigkeit: wächst, solange der Akteur keiner Gemeinde angehört. */
    public double belonging() {
        return belonging;
    }

    /** Wohnen: wächst, solange ein Mitglied kein Grundstück besitzt. */
    public double housing() {
        return housing;
    }

    /** Sicherheit: wie weit der Kontostand unter der persönlichen Rücklage liegt. */
    public double security() {
        return security;
    }

    /**
     * Schreibt die Bedürfnisse für einen neuen Tag fort.
     *
     * @param member          ob der Akteur einer Gemeinde angehört
     * @param homeless        ob er als Mitglied kein Grundstück besitzt
     * @param balance         sein Kontostand
     * @param savingsTarget   seine persönliche Rücklage
     * @param belongingGrowth Zuwachs der Zugehörigkeit pro Tag
     * @param housingGrowth   Zuwachs des Wohnbedürfnisses pro Tag
     */
    public void update(boolean member, boolean homeless, long balance, long savingsTarget,
                       double belongingGrowth, double housingGrowth) {
        belonging = member ? 0 : clamp(belonging + belongingGrowth);
        housing = member && homeless ? clamp(housing + housingGrowth) : 0;
        security = savingsTarget <= 0 ? 0 : clamp(1 - (double) balance / savingsTarget);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
