package de.etiennebader.civ.domain.war;

/**
 * Reine, deterministische Auflösung eines Gefechts aus Garnisonsgrößen.
 *
 * <p>Kein Zufall: Dieselben Eingaben ergeben immer dasselbe Ergebnis. Das ist eine bewusste
 * Vereinfachung für diese erste Ausbaustufe (siehe {@code docs/issue-27-krieg.md}) — spätere
 * Ausbaustufen können Ausrüstung, Training und Geländeeinfluss zusätzlich einrechnen, ohne dass
 * sich diese Schnittstelle ändern muss, solange am Ende eine Gefechtsstärke je Seite steht.
 *
 * <p>Gefechtsstärke je Seite: {@code Soldaten + Offiziere * OFFICER_WEIGHT}. Der Verteidiger
 * erhält zusätzlich den Heimvorteil {@link #DEFENDER_ADVANTAGE} (multiplikativ), weil er auf
 * eigenem Gebiet kämpft.
 */
public final class BattleResolver {

    /** Wie viele Soldaten ein Offizier bei der Gefechtsstärke wert ist. */
    public static final int OFFICER_WEIGHT = 3;

    /** Multiplikator auf die Gefechtsstärke des Verteidigers. */
    public static final double DEFENDER_ADVANTAGE = 1.1;

    /** Derselbe Heimvorteil in Prozent, für den exakten ganzzahligen Vergleich. */
    static final long DEFENDER_ADVANTAGE_PERCENT = 110;

    private BattleResolver() {
    }

    /** Gefechtsstärke einer Garnison. */
    public static double strength(int soldiers, int officers) {
        if (soldiers < 0 || officers < 0) {
            throw new IllegalArgumentException("Garnisonsgrößen dürfen nicht negativ sein");
        }
        return soldiers + (double) officers * OFFICER_WEIGHT;
    }

    /**
     * Löst ein Gefecht aus fertig berechneten Kampfkräften (Hundertstel Soldaten) auf; der Verteidiger erhält den
     * Heimvorteil. Ganzzahlig, damit ein exakter Gleichstand erkannt wird.
     */
    public static BattleOutcome resolvePower(long aggressorPower, long defenderPower) {
        long aggressor = aggressorPower * 100;
        long defender = defenderPower * DEFENDER_ADVANTAGE_PERCENT;
        if (aggressor > defender) {
            return BattleOutcome.AGGRESSOR_WINS;
        }
        return defender > aggressor ? BattleOutcome.DEFENDER_WINS : BattleOutcome.DRAW;
    }

    /**
     * Löst ein Gefecht zwischen Angreifer- und Verteidigergarnison auf.
     *
     * @param aggressorSoldiers Soldaten des Angreifers
     * @param aggressorOfficers Offiziere des Angreifers
     * @param defenderSoldiers  Soldaten des Verteidigers
     * @param defenderOfficers  Offiziere des Verteidigers
     */
    public static BattleOutcome resolve(int aggressorSoldiers, int aggressorOfficers, int defenderSoldiers,
                                        int defenderOfficers) {
        // Ganzzahlig verglichen (Heimvorteil 11/10), damit ein exakter Gleichstand nicht an Gleitkommafehlern scheitert
        long aggressor = (long) aggressorSoldiers + (long) aggressorOfficers * OFFICER_WEIGHT;
        long defender = ((long) defenderSoldiers + (long) defenderOfficers * OFFICER_WEIGHT) * DEFENDER_ADVANTAGE_PERCENT;
        aggressor *= 100;
        if (aggressorSoldiers < 0 || aggressorOfficers < 0 || defenderSoldiers < 0 || defenderOfficers < 0) {
            throw new IllegalArgumentException("Garnisonsgrößen dürfen nicht negativ sein");
        }
        if (aggressor > defender) {
            return BattleOutcome.AGGRESSOR_WINS;
        }
        if (defender > aggressor) {
            return BattleOutcome.DEFENDER_WINS;
        }
        return BattleOutcome.DRAW;
    }
}
