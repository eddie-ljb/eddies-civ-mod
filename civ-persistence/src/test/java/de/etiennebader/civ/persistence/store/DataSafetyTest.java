package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.persistence.PersistenceException;
import de.etiennebader.civ.persistence.migration.Migration;
import de.etiennebader.civ.persistence.migration.SchemaMigrations;
import de.etiennebader.civ.persistence.migration.SchemaMigrator;
import de.etiennebader.civ.persistence.sqlite.Backups;
import de.etiennebader.civ.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSafetyTest {

    private static List<Path> backups(Path database) throws IOException {
        Path folder = Backups.folder(database);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.toList();
        }
    }

    @Test
    @DisplayName("Eine alte Welt wird auf den neuesten Stand migriert; vorher entsteht eine Sicherung")
    void migratesOldWorldWithBackup() throws Exception {
        Path folder = Files.createTempDirectory("civ-migrate");
        Path file = folder.resolve("civ.sqlite");
        List<Migration> all = SchemaMigrations.all();
        try (SqliteDatabase database = SqliteDatabase.open(file)) {
            new SchemaMigrator(all.subList(0, 10)).migrate(database);
            database.inTransaction(connection -> {
                try (Statement insert = connection.createStatement()) {
                    insert.executeUpdate("INSERT INTO known_player (player_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Alt')");
                }
                return null;
            });
        }
        assertTrue(backups(file).isEmpty());
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            assertEquals(1L, (long) store.load().players().size());
        }
        List<Path> written = backups(file);
        assertEquals(1L, (long) written.size());
        assertTrue(written.getFirst().getFileName().toString().startsWith("civ-v10-"));
        try (SqliteDatabase backup = SqliteDatabase.open(written.getFirst())) {
            int version = backup.inTransaction(SchemaMigrator::currentVersion);
            assertEquals(10L, version);
        }
        try (SqliteDatabase database = SqliteDatabase.open(file)) {
            int version = database.inTransaction(SchemaMigrator::currentVersion);
            assertEquals((long) all.size(), (long) version);
        }
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            store.load();
            assertEquals(1L, (long) backups(file).size(), "aktuelle Welt: keine neue Sicherung");
        }
    }

    @Test
    @DisplayName("Sicherungen von Hand; von jeder Art bleiben die neuesten sieben")
    void manualBackupsArePruned() throws Exception {
        Path file = Files.createTempDirectory("civ-backup").resolve("civ.sqlite");
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            for (int i = 0; i < 9; i++) {
                Path backup = store.backupNow();
                assertTrue(Files.size(backup) > 0);
                Thread.sleep(3);
            }
        }
        assertEquals((long) Backups.KEEP, (long) backups(file).size());
    }

    @Test
    @DisplayName("Eine beschädigte Datei wird nicht geladen")
    void corruptFileIsRefused() throws Exception {
        Path file = Files.createTempDirectory("civ-corrupt").resolve("civ.sqlite");
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            store.load();
        }
        byte[] bytes = Files.readAllBytes(file);
        for (int i = 4096; i < Math.min(bytes.length, 4096 * 6); i++) {
            bytes[i] = (byte) 0x5A;
        }
        Files.write(file, bytes);
        assertThrows(RuntimeException.class, () -> SqliteStateStore.open(file).close());
    }
}
