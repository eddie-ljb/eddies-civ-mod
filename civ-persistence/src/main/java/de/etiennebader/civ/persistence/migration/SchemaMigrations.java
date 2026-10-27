package de.etiennebader.civ.persistence.migration;

import java.util.List;

/**
 * Alle Migrationen in Reihenfolge. Eine neue Schemaänderung bekommt eine neue Datei und einen
 * neuen Eintrag hier; bestehende Dateien werden nie mehr geändert.
 */
public final class SchemaMigrations {

    private static final List<String> FILES = List.of(
            "V001__initial_schema.sql",
            "V002__calendar_and_players.sql",
            "V003__polity_seat.sql");

    private SchemaMigrations() {
    }

    public static List<Migration> all() {
        return FILES.stream().map(Migration::fromResource).toList();
    }
}
