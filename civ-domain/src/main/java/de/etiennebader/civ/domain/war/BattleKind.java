package de.etiennebader.civ.domain.war;

/** Art eines geplanten Gefechts (Feldzug), aus Sicht der Seite, die es plant. */
public enum BattleKind {
    /** Frontalangriff: keine Boni. */
    ASSAULT,
    /** Hinterhalt: +25 % Kampfkraft, aber nur mit Ausspähern und wenn der Gegner keine hat. */
    AMBUSH,
    /** Verteidigung: +15 % Kampfkraft, nur für den Verteidiger des Kriegs. */
    DEFENSIVE
}
