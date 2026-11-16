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
    INVESTMENT,
    /** Umwelt und Natur: Wälder, Tiere, keine Explosionen. */
    ENVIRONMENT,
    /** Soziales: Gerechtigkeit, Hilfe für Schwache, Gehälter. */
    WELFARE,
    /** Wirtschaft und Handel: Märkte, Firmen, Börse. */
    ECONOMY,
    /** Bildung und Wissen. */
    EDUCATION,
    /** Tradition und Heimat. */
    TRADITION,
    /** Fortschritt und Technik. */
    PROGRESS,
    /** Frieden und Diplomatie: Bündnisse, keine Kämpfe. */
    PEACE,
    /** Stärke und Expansion: mehr Gebiet, Verteidigung. */
    EXPANSION,
    /** Bürgerbeteiligung: Abstimmungen, Transparenz, Pressefreiheit. */
    DEMOCRACY,
    /** Landwirtschaft und Versorgung. */
    AGRICULTURE,
    /** Heimat zuerst: Selbstbestimmung der Gemeinde gegenüber dem Bund. */
    AUTONOMY
}
