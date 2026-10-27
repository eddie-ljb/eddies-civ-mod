package de.etiennebader.civ.persistence.sqlite;

import de.etiennebader.civ.persistence.PersistenceException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Eine geöffnete SQLite-Datei mit genau einer Verbindung.
 *
 * <p>Geschrieben wird gebündelt beim Speichern der Welt, deshalb genügt eine Verbindung.
 * Fremdschlüssel sind eingeschaltet. Der Journal-Modus bleibt beim Standard (Rollback-Journal),
 * damit nach jedem Commit eine einzelne, vollständige Datei vorliegt, die Backup-Werkzeuge
 * zusammen mit dem Weltordner kopieren können.
 *
 * <p>Der Treiber wird direkt über {@link SQLiteDataSource} angesprochen statt über den
 * {@code DriverManager}. Das funktioniert auch in Fabrics eingebetteten JARs, wo die
 * Service-Suche des {@code DriverManager} den Treiber nicht immer findet.
 *
 * <p>Threadsicher: Transaktionen laufen nacheinander, auch wenn Laden und Speichern aus
 * verschiedenen Threads kommen.
 */
public final class SqliteDatabase implements AutoCloseable {

    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Path file;
    private final Connection connection;

    private SqliteDatabase(Path file, Connection connection) {
        this.file = file;
        this.connection = connection;
    }

    /**
     * Öffnet die Datei; fehlt sie, wird sie mitsamt Verzeichnissen angelegt.
     *
     * @throws PersistenceException wenn die Datei nicht geöffnet werden kann
     */
    public static SqliteDatabase open(Path file) {
        Objects.requireNonNull(file, "file");
        Path absolute = file.toAbsolutePath();
        try {
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
            SQLiteDataSource dataSource = new SQLiteDataSource(config);
            dataSource.setUrl("jdbc:sqlite:" + absolute);
            return new SqliteDatabase(absolute, dataSource.getConnection());
        } catch (IOException | SQLException e) {
            throw new PersistenceException("Datenbank kann nicht geöffnet werden: " + absolute, e);
        }
    }

    public Path file() {
        return file;
    }

    /**
     * Führt die Arbeit in einer Transaktion aus: Commit bei Erfolg, Rollback bei jedem Fehler.
     *
     * @throws PersistenceException bei SQL-Fehlern; andere Laufzeitfehler werden nach dem Rollback weitergereicht
     */
    public synchronized <T> T inTransaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Datenbankzugriff fehlgeschlagen: " + file, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new PersistenceException("Datenbank kann nicht geschlossen werden: " + file, e);
        }
    }
}
