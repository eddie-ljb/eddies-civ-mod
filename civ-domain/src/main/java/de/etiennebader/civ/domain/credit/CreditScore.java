package de.etiennebader.civ.domain.credit;

/**
 * Kreditscore zwischen {@value #MIN} und {@value #MAX}; je höher, desto günstiger der Kredit.
 * Neue Teilnehmer starten bei {@value #START}.
 */
public final class CreditScore {

    public static final int MIN = 300;
    public static final int MAX = 850;
    public static final int START = 600;
    /** Zuwachs bei vollständiger, pünktlicher Rückzahlung. */
    public static final int REPAID_BONUS = 20;
    /** Abzug bei Zahlungsausfall. */
    public static final int DEFAULT_PENALTY = 100;

    private CreditScore() {
    }

    public static int clamp(int score) {
        return Math.max(MIN, Math.min(MAX, score));
    }

    /**
     * Zinsaufschlag in Basispunkten (1/100 Prozent) auf den Leitzins: 0 bei Höchstscore, 1100 bei Mindestscore.
     */
    public static int premiumBasisPoints(int score) {
        return (MAX - clamp(score)) * 2;
    }

    /** Jahreszins in Basispunkten: Leitzins der Währung plus Aufschlag nach Kreditscore. */
    public static int rateBasisPoints(int baseRatePercent, int score) {
        return baseRatePercent * 100 + premiumBasisPoints(score);
    }
}
