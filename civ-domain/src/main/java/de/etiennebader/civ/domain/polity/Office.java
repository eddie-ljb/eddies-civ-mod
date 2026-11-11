package de.etiennebader.civ.domain.polity;

/**
 * Ämter einer Gemeinde. Jedes Amt hat höchstens einen Inhaber.
 */
public enum Office {
    /** Leitet die Gemeinde: Gesetze ohne Abstimmung, Sitz verlegen, auflösen, Ämter besetzen. */
    MAYOR,
    /** Verwaltet die Gemeindekasse. */
    TREASURER,
    /** Bauamt: legt Grundstücke an, bietet sie an, vergibt und löst sie auf. */
    PLANNER
}
