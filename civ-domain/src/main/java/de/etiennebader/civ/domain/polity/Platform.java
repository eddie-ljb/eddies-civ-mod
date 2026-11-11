package de.etiennebader.civ.domain.polity;

/**
 * Wahlprogramm einer Kandidatur. Dorfbewohner mit Wahlrecht wählen eher, wer ihren Ansichten entspricht.
 */
public enum Platform {
    /** Ohne Programm: nur Zufriedenheit mit dem Amtsinhaber zählt. */
    NONE,
    /** Sicherheit und Ordnung: strenge Schutzregeln. */
    ORDER,
    /** Freiheit: wenig Verbote. */
    FREEDOM,
    /** Niedrige Steuern. */
    LOW_TAXES,
    /** Ausbau der Gemeinde: Steuern für Investitionen. */
    INVESTMENT
}
