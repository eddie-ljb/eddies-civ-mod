package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabelle {@code claim}: ein Eintrag pro beanspruchtem Chunk.
 */
final class TerritoryTable {

    private static final String UPSERT = """
            INSERT INTO claim (dimension, chunk_x, chunk_z, polity_id, type, since) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (dimension, chunk_x, chunk_z) DO UPDATE SET
                polity_id = excluded.polity_id, type = excluded.type, since = excluded.since""";
    private static final String DELETE = "DELETE FROM claim WHERE dimension = ? AND chunk_x = ? AND chunk_z = ?";

    /** Schreibt den aktuellen Stand der Chunks; nicht mehr beanspruchte werden gelöscht. */
    void write(Connection connection, Iterable<ChunkCoord> chunks, TerritoryView territory) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT);
             PreparedStatement delete = connection.prepareStatement(DELETE)) {
            for (ChunkCoord chunk : chunks) {
                Optional<Claim> claim = territory.claimAt(chunk);
                if (claim.isPresent()) {
                    bindChunk(upsert, chunk);
                    upsert.setString(4, claim.get().polity().value().toString());
                    upsert.setString(5, claim.get().type().name());
                    upsert.setLong(6, claim.get().since().value());
                    upsert.addBatch();
                } else {
                    bindChunk(delete, chunk);
                    delete.addBatch();
                }
            }
            delete.executeBatch();
            upsert.executeBatch();
        }
    }

    private static void bindChunk(PreparedStatement statement, ChunkCoord chunk) throws SQLException {
        statement.setString(1, chunk.dimension().value());
        statement.setInt(2, chunk.x());
        statement.setInt(3, chunk.z());
    }

    Map<ChunkCoord, Claim> loadAll(Connection connection) throws SQLException {
        Map<ChunkCoord, Claim> claims = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT dimension, chunk_x, chunk_z, polity_id, type, since FROM claim");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                ChunkCoord chunk = new ChunkCoord(new DimensionId(rows.getString("dimension")),
                        rows.getInt("chunk_x"), rows.getInt("chunk_z"));
                claims.put(chunk, new Claim(new PolityId(UUID.fromString(rows.getString("polity_id"))),
                        ClaimType.valueOf(rows.getString("type")), Day.of(rows.getLong("since"))));
            }
        }
        return claims;
    }
}
