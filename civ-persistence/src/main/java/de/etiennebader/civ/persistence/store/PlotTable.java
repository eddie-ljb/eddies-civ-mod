package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.PlotKey;
import de.etiennebader.civ.application.persistence.StateDelta.PlotChange;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotSnapshot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabelle {@code plot}. Neue Grundstücke bekommen die nächste Nummer in {@code seq}; dadurch bleibt
 * die Anlagereihenfolge des Grundbuchs über Speichern und Laden erhalten.
 */
final class PlotTable {

    private static final String UPSERT = """
            INSERT INTO plot (id, polity_id, seq, type, dimension, min_x, min_z, max_x, max_z,
                              holder_kind, holder_id, holder_since)
            VALUES (?, ?, (SELECT COALESCE(MAX(seq), 0) + 1 FROM plot), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET polity_id = excluded.polity_id, type = excluded.type,
                dimension = excluded.dimension, min_x = excluded.min_x, min_z = excluded.min_z,
                max_x = excluded.max_x, max_z = excluded.max_z, holder_kind = excluded.holder_kind,
                holder_id = excluded.holder_id, holder_since = excluded.holder_since""";

    /**
     * Schreibt die Grundstücke in der Reihenfolge der Änderungen; aufgelöste werden gelöscht.
     * Einzeln statt im Batch, damit jedes neue Grundstück die nächste {@code seq} sieht.
     */
    void write(Connection connection, Iterable<PlotChange> changes) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM plot WHERE id = ?")) {
            for (PlotChange change : changes) {
                PlotKey key = change.key();
                Optional<PlotSnapshot> plot = change.plot();
                if (plot.isPresent()) {
                    bind(upsert, key, plot.get());
                    upsert.executeUpdate();
                } else {
                    delete.setString(1, key.plot().value().toString());
                    delete.executeUpdate();
                }
            }
        }
    }

    private static void bind(PreparedStatement upsert, PlotKey key, PlotSnapshot plot) throws SQLException {
        Area area = plot.area();
        upsert.setString(1, plot.id().value().toString());
        upsert.setString(2, key.polity().value().toString());
        upsert.setString(3, plot.type().name());
        upsert.setString(4, area.dimension().value());
        upsert.setInt(5, area.minX());
        upsert.setInt(6, area.minZ());
        upsert.setInt(7, area.maxX());
        upsert.setInt(8, area.maxZ());
        upsert.setString(9, ActorCodec.kindOf(plot.holder()));
        upsert.setString(10, plot.holder().uuid().toString());
        upsert.setLong(11, plot.holderSince().value());
    }

    /** Alle Grundstücke je Polity-ID in Anlagereihenfolge. */
    Map<String, List<PlotSnapshot>> loadAll(Connection connection) throws SQLException {
        Map<String, List<PlotSnapshot>> plots = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, polity_id, type, dimension, min_x, min_z, max_x, max_z, holder_kind, holder_id, holder_since
                FROM plot ORDER BY seq""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                Area area = new Area(new DimensionId(rows.getString("dimension")), rows.getInt("min_x"),
                        rows.getInt("min_z"), rows.getInt("max_x"), rows.getInt("max_z"));
                plots.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new PlotSnapshot(
                        new PlotId(UUID.fromString(rows.getString("id"))), area, PlotType.valueOf(rows.getString("type")),
                        ActorCodec.decode(rows.getString("holder_kind"), rows.getString("holder_id")),
                        Day.of(rows.getLong("holder_since"))));
            }
        }
        return plots;
    }
}
