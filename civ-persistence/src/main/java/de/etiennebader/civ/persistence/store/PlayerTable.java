package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.population.Gender;
import de.etiennebader.civ.domain.shared.PlayerId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bekannte Spielernamen in der Tabelle {@code known_player}.
 */
final class PlayerTable {

    private static final String UPSERT = """
            INSERT INTO known_player (player_id, name, gender) VALUES (?, ?, ?)
            ON CONFLICT (player_id) DO UPDATE SET name = excluded.name, gender = excluded.gender""";

    void write(Connection connection, Iterable<KnownPlayer> players) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
            for (KnownPlayer player : players) {
                upsert.setString(1, player.player().uuid().toString());
                upsert.setString(2, player.name());
                upsert.setString(3, player.gender().map(Gender::name).orElse(null));
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
    }

    List<KnownPlayer> loadAll(Connection connection) throws SQLException {
        List<KnownPlayer> players = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT player_id, name, gender FROM known_player ORDER BY name, player_id");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                PlayerRef player = new PlayerRef(new PlayerId(UUID.fromString(rows.getString("player_id"))));
                String gender = rows.getString("gender");
                players.add(new KnownPlayer(player, rows.getString("name"),
                        Optional.ofNullable(gender).map(Gender::valueOf)));
            }
        }
        return players;
    }
}
