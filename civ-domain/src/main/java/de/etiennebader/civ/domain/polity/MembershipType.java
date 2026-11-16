package de.etiennebader.civ.domain.polity;

/**
 * Art der Zugehörigkeit zu einer Polity.
 */
public enum MembershipType {
    /** Bürger mit vollen Rechten. */
    CITIZEN,
    /** Einwohner ohne Bürgerrecht. */
    RESIDENT,
    /** Gliedkörperschaft eines Bundes: eine Gemeinde oder ein niedrigerer Bund. Kein Wähler. */
    CONSTITUENT
}
