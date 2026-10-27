package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolitySnapshot;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabellen {@code polity}, {@code membership}, {@code statute} und {@code statute_value}.
 * Eine geänderte Polity wird vollständig neu geschrieben; ihre Gründungsreihenfolge ({@code seq})
 * bleibt dabei erhalten.
 */
final class PolityTable {

    private static final String UPSERT_POLITY = """
            INSERT INTO polity (id, seq, name, kind, tier, currency, founded_on, seat_dimension, seat_x, seat_y, seat_z)
            VALUES (?, (SELECT COALESCE(MAX(seq), 0) + 1 FROM polity), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = excluded.name, kind = excluded.kind, tier = excluded.tier,
                currency = excluded.currency, founded_on = excluded.founded_on,
                seat_dimension = excluded.seat_dimension, seat_x = excluded.seat_x, seat_y = excluded.seat_y,
                seat_z = excluded.seat_z""";

    private final ParameterCatalog catalog;

    PolityTable(ParameterCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    void write(Connection connection, PolitySnapshot polity) throws SQLException {
        String id = polity.id().value().toString();
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT_POLITY)) {
            upsert.setString(1, id);
            upsert.setString(2, polity.name().value());
            upsert.setString(3, polity.kind().name());
            upsert.setString(4, polity.tier().name());
            upsert.setString(5, polity.currency().code());
            upsert.setLong(6, polity.foundedOn().value());
            bindSeat(upsert, 7, polity.seat());
            upsert.executeUpdate();
        }
        deleteChildren(connection, id);
        insertMembers(connection, id, polity.members());
        insertStatutes(connection, id, polity.statutes());
    }

    private static void bindSeat(PreparedStatement statement, int firstIndex, Optional<BlockPosition> seat)
            throws SQLException {
        if (seat.isEmpty()) {
            statement.setNull(firstIndex, Types.VARCHAR);
            for (int offset = 1; offset <= 3; offset++) {
                statement.setNull(firstIndex + offset, Types.INTEGER);
            }
            return;
        }
        statement.setString(firstIndex, seat.get().dimension().value());
        statement.setInt(firstIndex + 1, seat.get().x());
        statement.setInt(firstIndex + 2, seat.get().y());
        statement.setInt(firstIndex + 3, seat.get().z());
    }

    private static Optional<BlockPosition> seatOf(ResultSet row) throws SQLException {
        String dimension = row.getString("seat_dimension");
        if (dimension == null) {
            return Optional.empty();
        }
        return Optional.of(new BlockPosition(new DimensionId(dimension), row.getInt("seat_x"), row.getInt("seat_y"),
                row.getInt("seat_z")));
    }

    void delete(Connection connection, PolityId polity) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM polity WHERE id = ?")) {
            delete.setString(1, polity.value().toString());
            delete.executeUpdate();
        }
    }

    private static void deleteChildren(Connection connection, String polityId) throws SQLException {
        for (String table : List.of("membership", "statute")) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE polity_id = ?")) {
                delete.setString(1, polityId);
                delete.executeUpdate();
            }
        }
    }

    private static void insertMembers(Connection connection, String polityId, Iterable<Membership> members) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO membership (polity_id, position, actor_kind, actor_id, type, since) VALUES (?, ?, ?, ?, ?, ?)")) {
            int position = 0;
            for (Membership membership : members) {
                insert.setString(1, polityId);
                insert.setInt(2, position++);
                insert.setString(3, ActorCodec.kindOf(membership.member()));
                insert.setString(4, membership.member().uuid().toString());
                insert.setString(5, membership.type().name());
                insert.setLong(6, membership.since().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertStatutes(Connection connection, String polityId, List<EnactedStatute> statutes) throws SQLException {
        try (PreparedStatement insertStatute = connection.prepareStatement(
                "INSERT INTO statute (id, polity_id, position, title, enacted_on) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement insertValue = connection.prepareStatement(
                     "INSERT INTO statute_value (statute_id, position, parameter, value) VALUES (?, ?, ?, ?)")) {
            int position = 0;
            for (EnactedStatute enacted : statutes) {
                String statuteId = enacted.statute().id().value().toString();
                insertStatute.setString(1, statuteId);
                insertStatute.setString(2, polityId);
                insertStatute.setInt(3, position++);
                insertStatute.setString(4, enacted.statute().title());
                insertStatute.setLong(5, enacted.enactedOn().value());
                insertStatute.addBatch();
                int valuePosition = 0;
                for (ParameterValue<?> value : enacted.statute().values()) {
                    insertValue.setString(1, statuteId);
                    insertValue.setInt(2, valuePosition++);
                    insertValue.setString(3, value.key().id());
                    insertValue.setString(4, ParameterCatalog.encode(value));
                    insertValue.addBatch();
                }
            }
            insertStatute.executeBatch();
            insertValue.executeBatch();
        }
    }

    /** Alle Polities in Gründungsreihenfolge. */
    List<Polity> loadAll(Connection connection) throws SQLException {
        Map<String, List<Membership>> members = loadMembers(connection);
        Map<String, List<EnactedStatute>> statutes = loadStatutes(connection);
        List<Polity> polities = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
                SELECT id, name, kind, tier, currency, founded_on, seat_dimension, seat_x, seat_y, seat_z
                FROM polity ORDER BY seq""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                polities.add(Polity.restore(new PolityId(UUID.fromString(id)), new PolityName(rows.getString("name")),
                        PolityKind.valueOf(rows.getString("kind")), SettlementTier.valueOf(rows.getString("tier")),
                        new CurrencyId(rows.getString("currency")), Day.of(rows.getLong("founded_on")), seatOf(rows),
                        members.getOrDefault(id, List.of()), statutes.getOrDefault(id, List.of())));
            }
        }
        return polities;
    }

    private static Map<String, List<Membership>> loadMembers(Connection connection) throws SQLException {
        Map<String, List<Membership>> members = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT polity_id, actor_kind, actor_id, type, since FROM membership ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                members.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new Membership(
                        ActorCodec.decode(rows.getString("actor_kind"), rows.getString("actor_id")),
                        MembershipType.valueOf(rows.getString("type")), Day.of(rows.getLong("since"))));
            }
        }
        return members;
    }

    private Map<String, List<EnactedStatute>> loadStatutes(Connection connection) throws SQLException {
        Map<String, List<ParameterValue<?>>> values = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT statute_id, parameter, value FROM statute_value ORDER BY statute_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                values.computeIfAbsent(rows.getString("statute_id"), ignored -> new ArrayList<>())
                        .add(catalog.decode(rows.getString("parameter"), rows.getString("value")));
            }
        }
        Map<String, List<EnactedStatute>> statutes = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, polity_id, title, enacted_on FROM statute ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                Statute statute = new Statute(new StatuteId(UUID.fromString(id)), rows.getString("title"),
                        values.getOrDefault(id, List.of()));
                statutes.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>())
                        .add(new EnactedStatute(statute, Day.of(rows.getLong("enacted_on"))));
            }
        }
        return statutes;
    }
}
