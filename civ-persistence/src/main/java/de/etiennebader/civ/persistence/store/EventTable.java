package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.persistence.json.EventJson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Tabelle {@code domain_event}: das Audit-Log. Events werden nur angehängt, nie geändert.
 */
final class EventTable {

    void append(Connection connection, Iterable<DomainEvent> events) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO domain_event (day, type, payload) VALUES (?, ?, ?)")) {
            for (DomainEvent event : events) {
                insert.setLong(1, event.occurredOn().value());
                insert.setString(2, event.getClass().getSimpleName());
                insert.setString(3, EventJson.toJson(event));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
