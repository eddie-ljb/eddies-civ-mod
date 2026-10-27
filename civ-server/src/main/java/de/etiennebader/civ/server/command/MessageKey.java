package de.etiennebader.civ.server.command;

import java.util.Locale;

/**
 * Alle Texte, die Befehle und Ankündigungen ausgeben. Der Schlüssel in den Sprachdateien ist
 * {@code eddies-civ-mod.command.<name in Kleinbuchstaben>}.
 */
public enum MessageKey {

    // Gemeinde
    FOUNDED,
    JOINED,
    LEFT,
    NOT_IN_MUNICIPALITY,
    UNKNOWN_POLITY_NAMED,
    // Gebiet
    CLAIMED,
    RELEASED,
    // Gemeindehaus
    TOWN_HALL_NEEDS_NAME,
    TOWN_HALL_ORPHANED,
    // Gebietsschutz
    PROTECTED_TERRITORY,
    PROTECTED_PLOT,
    PROTECTED_SEAT,
    BYPASS_ON,
    BYPASS_OFF,
    // Recht
    LAWS_HEADER,
    LAW_ENTRY,
    LAW_AMENDED,
    UNKNOWN_LAW,
    INVALID_LAW_VALUE,
    // Übersicht über sich selbst
    INFO_MUNICIPALITY,
    INFO_NO_MUNICIPALITY,
    INFO_CHUNK_CLAIMED,
    INFO_CHUNK_WILDERNESS,
    INFO_BALANCE,
    INFO_DAY,
    // Übersicht über eine Gemeinde
    POLITY_HEADER,
    POLITY_DETAILS,
    POLITY_MEMBERS,
    POLITY_TERRITORY,
    // Liste aller Gemeinden
    LIST_HEADER,
    LIST_ENTRY,
    LIST_EMPTY,
    // Ankündigungen und Fehler ohne Domänenbezug
    NEW_DAY,
    INTERNAL_ERROR;

    private static final String PREFIX = "eddies-civ-mod.command.";

    public String key() {
        return PREFIX + name().toLowerCase(Locale.ROOT);
    }
}
