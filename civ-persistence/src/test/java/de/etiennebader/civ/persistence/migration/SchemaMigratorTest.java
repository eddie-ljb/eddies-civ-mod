package de.etiennebader.civ.persistence.migration;

import de.etiennebader.civ.persistence.PersistenceException;
import de.etiennebader.civ.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorTest {

    @Test
    @DisplayName("Leere Datei bekommt das vollständige Schema, ein zweiter Lauf ändert nichts")
    void migratesEmptyDatabaseOnce() throws IOException {
        try (SqliteDatabase database = SqliteDatabase.open(tempFile())) {
            SchemaMigrator migrator = SchemaMigrator.standard();

            assertEquals(0, migrator.migrate(database));
            assertEquals(migrator.latestVersion(), migrator.migrate(database));
            assertTrue(tableExists(database, "polity"));
            assertTrue(tableExists(database, "domain_event"));
        }
    }

    @Test
    @DisplayName("Bestehende Datei wird schrittweise hochgezogen")
    void appliesOnlyMissingMigrations() throws IOException {
        Migration first = new Migration(1, "erste", List.of("CREATE TABLE a (x INTEGER)"));
        Migration second = new Migration(2, "zweite", List.of("CREATE TABLE b (y INTEGER)"));
        try (SqliteDatabase database = SqliteDatabase.open(tempFile())) {
            new SchemaMigrator(List.of(first)).migrate(database);

            assertEquals(1, new SchemaMigrator(List.of(first, second)).migrate(database));
            assertTrue(tableExists(database, "b"));
        }
    }

    @Test
    @DisplayName("Fehlerhafte Migration wird vollständig zurückgerollt")
    void failedMigrationRollsBack() throws IOException {
        Migration broken = new Migration(1, "kaputt", List.of("CREATE TABLE ok (x INTEGER)", "CREATE TABL kaputt"));
        try (SqliteDatabase database = SqliteDatabase.open(tempFile())) {
            assertThrows(PersistenceException.class, () -> new SchemaMigrator(List.of(broken)).migrate(database));

            assertFalse(tableExists(database, "ok"));
            assertEquals(0, (int) database.inTransaction(SchemaMigrator::currentVersion));
        }
    }

    @Test
    @DisplayName("Neuere Datenbank als der Mod → wird nicht angefasst")
    void rejectsNewerDatabase() throws IOException {
        Migration first = new Migration(1, "erste", List.of("CREATE TABLE a (x INTEGER)"));
        Migration second = new Migration(2, "zweite", List.of("CREATE TABLE b (y INTEGER)"));
        try (SqliteDatabase database = SqliteDatabase.open(tempFile())) {
            new SchemaMigrator(List.of(first, second)).migrate(database);

            assertThrows(UnsupportedSchemaVersionException.class, () -> new SchemaMigrator(List.of(first)).migrate(database));
        }
    }

    @Test
    @DisplayName("Lücken in der Nummerierung sind ein Programmierfehler")
    void rejectsGaps() {
        Migration second = new Migration(2, "zweite", List.of("SELECT 1"));
        assertThrows(IllegalArgumentException.class, () -> new SchemaMigrator(List.of(second)));
    }

    @Test
    @DisplayName("Skripte werden an Semikolons am Zeilenende zerlegt, Kommentare ignoriert")
    void splitsStatements() {
        assertEquals(List.of("CREATE TABLE a (\n    x INTEGER\n)", "CREATE INDEX i ON a (x)"),
                Migration.split("-- Kommentar\nCREATE TABLE a (\n    x INTEGER\n);\n\nCREATE INDEX i ON a (x);\n"));
        assertThrows(IllegalArgumentException.class, () -> Migration.split("SELECT 1"));
    }

    static Path tempFile() throws IOException {
        Path directory = Files.createTempDirectory("civ-persistence");
        return directory.resolve("civ.sqlite");
    }

    private static boolean tableExists(SqliteDatabase database, String table) {
        return database.inTransaction(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
                result.next();
                return result.getInt(1) == 1;
            }
        });
    }
}
