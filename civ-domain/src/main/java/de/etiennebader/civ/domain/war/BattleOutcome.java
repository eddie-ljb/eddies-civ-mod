package de.etiennebader.civ.domain.war;

/** Ergebnis eines aufgelösten Gefechts. */
public enum BattleOutcome {
    /** Der Angreifer hat die höhere Gefechtsstärke. */
    AGGRESSOR_WINS,
    /** Der Verteidiger hat die höhere (oder durch Heimvorteil gleich hohe) Gefechtsstärke. */
    DEFENDER_WINS,
    /** Beide Seiten haben exakt dieselbe Gefechtsstärke, auch nach Heimvorteil. */
    DRAW
}
