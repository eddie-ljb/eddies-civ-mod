package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.domain.shared.Day;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Der Kalender in der Tabelle {@code world_clock}: genau eine Zeile.
 */
final class CalendarTable {

    private static final String UPSERT = """
            INSERT INTO world_clock (id, today, ticks_into_day) VALUES (1, ?, ?)
            ON CONFLICT (id) DO UPDATE SET today = excluded.today, ticks_into_day = excluded.ticks_into_day""";

    void write(Connection connection, CalendarState calendar) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
            upsert.setLong(1, calendar.today().value());
            upsert.setLong(2, calendar.ticksIntoDay());
            upsert.executeUpdate();
        }
    }

    /** Der gespeicherte Stand; bei einer neuen Welt der Anfang. */
    CalendarState load(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT today, ticks_into_day FROM world_clock");
             ResultSet rows = select.executeQuery()) {
            if (!rows.next()) {
                return CalendarState.START;
            }
            return new CalendarState(Day.of(rows.getLong("today")), rows.getLong("ticks_into_day"));
        }
    }
}
