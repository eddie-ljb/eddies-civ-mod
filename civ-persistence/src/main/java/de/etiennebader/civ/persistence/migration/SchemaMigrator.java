package de.etiennebader.civ.persistence.migration;

import de.etiennebader.civ.persistence.sqlite.SqliteDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * Bringt eine Datenbank auf den neuesten Schemastand. Jede Migration läuft in einer eigenen
 * Transaktion zusammen mit ihrem Eintrag in {@code schema_version}: Sie gilt ganz oder gar nicht.
 */
public final class SchemaMigrator {

    private static final String CREATE_VERSION_TABLE = """
            CREATE TABLE IF NOT EXISTS schema_version (
                version      INTEGER NOT NULL PRIMARY KEY,
                description  TEXT    NOT NULL,
                installed_at TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""";

    private final List<Migration> migrations;

    /**
     * @param migrations lückenlos aufsteigend ab Version 1
     */
    public SchemaMigrator(List<Migration> migrations) {
        this.migrations = List.copyOf(Objects.requireNonNull(migrations, "migrations"));
        for (int index = 0; index < this.migrations.size(); index++) {
            if (this.migrations.get(index).version() != index + 1) {
                throw new IllegalArgumentException("Migrationen müssen lückenlos ab 1 nummeriert sein, gefunden: "
                        + this.migrations.get(index).version() + " an Stelle " + (index + 1));
            }
        }
    }

    /** Migrator mit allen Migrationen des Mods. */
    public static SchemaMigrator standard() {
        return new SchemaMigrator(SchemaMigrations.all());
    }

    public int latestVersion() {
        return migrations.size();
    }

    /**
     * Führt alle noch fehlenden Migrationen aus.
     *
     * @return die Schemaversion vor der Migration
     * @throws UnsupportedSchemaVersionException wenn die Datenbank neuer ist als der Mod
     */
    public int migrate(SqliteDatabase database) {
        int current = database.inTransaction(SchemaMigrator::currentVersion);
        if (current > latestVersion()) {
            throw new UnsupportedSchemaVersionException(current, latestVersion());
        }
        for (Migration migration : migrations.subList(current, migrations.size())) {
            database.inTransaction(connection -> {
                apply(connection, migration);
                return null;
            });
        }
        return current;
    }

    /** Aktuelle Schemaversion; 0 für eine leere Datenbank. */
    public static int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_VERSION_TABLE);
            try (ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void apply(Connection connection, Migration migration) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : migration.statements()) {
                statement.execute(sql);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO schema_version (version, description) VALUES (?, ?)")) {
            insert.setInt(1, migration.version());
            insert.setString(2, migration.description());
            insert.executeUpdate();
        }
    }
}
