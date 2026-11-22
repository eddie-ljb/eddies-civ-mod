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
            "V003__polity_seat.sql",
            "V004__market.sql",
            "V005__plot_offer.sql",
            "V006__proposals.sql",
            "V007__shops.sql",
            "V008__offices_and_elections.sql",
            "V009__election_platforms.sql",
            "V010__polity_level.sql",
            "V011__constitution.sql",
            "V012__law_book.sql",
            "V013__parties.sql",
            "V014__parliament.sql",
            "V015__campaign.sql",
            "V016__civil.sql", "V017__exchange_press.sql", "V018__civic_justice.sql", "V019__production.sql",
            "V020__party_program_mode.sql",
            "V021__polity_image.sql",
            "V022__party_area.sql",
            "V023__home_ports.sql",
            "V024__candidate_district.sql",
            "V025__player_gender.sql",
            "V026__currencies_and_credit.sql",
            "V027__loan_offers.sql");

    private SchemaMigrations() {
    }

    public static List<Migration> all() {
        return FILES.stream().map(Migration::fromResource).toList();
    }
}
