package de.etiennebader.civ.domain.polity;

/**
 * Wie eine Partei ihr Programm (Richtung, Wappen) ändert.
 */
public enum ProgramMode {
    /** Der Vorsitz entscheidet allein und sofort. */
    CHAIR,
    /** Änderungen sind Anträge, über die die Mitglieder abstimmen. */
    VOTE
}
