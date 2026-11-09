package de.etiennebader.civ.application.polity;

/**
 * Stand einer Abstimmung nach einem Antrag oder einer Stimme.
 */
public enum VoteOutcome {
    /** Noch keine Mehrheit; die Abstimmung läuft weiter. */
    OPEN,
    /** Angenommen; die Regelung gilt jetzt. */
    ACCEPTED,
    /** Abgelehnt; nichts ändert sich. */
    REJECTED
}
