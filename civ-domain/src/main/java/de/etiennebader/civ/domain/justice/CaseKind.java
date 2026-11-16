package de.etiennebader.civ.domain.justice;

/** Art eines Verfahrens. */
public enum CaseKind {
    /** Strafverfahren: Anklage wegen eines Vergehens; Strafe an die Kasse, Verbannung, Amtsenthebung. */
    CRIMINAL,
    /** Zivilklage: Streit zwischen zwei Parteien; Schadenersatz an den Kläger. */
    CIVIL,
    /** Verfassungsklage: Ein Gesetz oder eine Regelung verstößt gegen die Verfassung und wird nichtig. */
    CONSTITUTIONAL
}
