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
     * Löst ein Gefecht zwischen Angreifer- und Verteidigergarnison auf.
     *
     * @param aggressorSoldiers Soldaten des Angreifers
     * @param aggressorOfficers Offiziere des Angreifers
     * @param defenderSoldiers  Soldaten des Verteidigers
     * @param defenderOfficers  Offiziere des Verteidigers
     */
    public static BattleOutcome resolve(int aggressorSoldiers, int aggressorOfficers, int defenderSoldiers,
                                        int defenderOfficers) {
        double aggressor = strength(aggressorSoldiers, aggressorOfficers);
        double defender = strength(defenderSoldiers, defenderOfficers) * DEFENDER_ADVANTAGE;
        if (aggressor > defender) {
            return BattleOutcome.AGGRESSOR_WINS;
        }
        if (defender > aggressor) {
            return BattleOutcome.DEFENDER_WINS;
        }
        return BattleOutcome.DRAW;
    }
}
