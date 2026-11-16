package de.etiennebader.civ.domain.contract;

/**
 * Warum ein Vertrag endete.
 */
public enum EndReason {
    /** Die vereinbarte Laufzeit ist um. */
    EXPIRED,
    /** Eine Seite hat gekündigt; die Kündigungsfrist ist abgelaufen. */
    NOTICE,
    /** Der Zahler konnte nicht zahlen. */
    BREACH,
    /** Der Gegenstand fiel weg, z. B. das Grundstück wurde aufgelöst oder eine Seite verschwand. */
    VOID
}
