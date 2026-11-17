package de.etiennebader.civ.persistence.sqlite;

import de.etiennebader.civ.persistence.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sicherungskopien der Datenbank im Ordner {@code backups} neben der Datei.
 *
 * <p>Kopiert wird mit {@code VACUUM INTO}: SQLite schreibt dabei eine in sich stimmige, verdichtete Kopie, auch während
 * die Datenbank offen ist. Von jeder Art (z. B. {@code daily}, {@code migration}) bleiben die neuesten
 * {@link #KEEP} Kopien.
 */
public final class Backups {

    public static final int KEEP = 7;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private Backups() {
    }

    /** Ordner der Sicherungen zu einer Datenbankdatei. */
    public static Path folder(Path database) {
        Path parent = database.toAbsolutePath().getParent();
        return (parent == null ? Path.of(".") : parent).resolve("backups");
    }

    /**
     * Schreibt eine Sicherung und räumt alte derselben Art weg. Muss außerhalb einer Transaktion laufen.
     *
     * @param kind Art der Sicherung, nur Buchstaben, Ziffern und Bindestriche
     * @return die neue Datei
     */
    public static Path write(Connection connection, Path database, String kind) {
        if (!kind.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Ungültige Art: " + kind);
        }
        Path folder = folder(database);
        String base = database.getFileName().toString().replaceFirst("\\.sqlite$", "");
        Path target = folder.resolve(base + "-" + kind + "-" + LocalDateTime.now().format(STAMP) + ".sqlite");
        try {
            Files.createDirectories(folder);
            Files.deleteIfExists(target);
            try (Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            prune(folder, base + "-" + kind + "-");
            return target;
        } catch (IOException | SQLException e) {
            throw new PersistenceException("Sicherung fehlgeschlagen: " + target, e);
        }
    }

    /** Ob die Datenbank in Ordnung ist ({@code PRAGMA quick_check}). */
    public static List<String> problems(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA quick_check")) {
            java.util.ArrayList<String> problems = new java.util.ArrayList<>();
            while (rows.next()) {
                String line = rows.getString(1);
                if (!"ok".equalsIgnoreCase(line)) {
                    problems.add(line);
                }
            }
            return problems;
        } catch (SQLException e) {
            return List.of(e.getMessage());
        }
    }

    private static void prune(Path folder, String prefix) throws IOException {
        List<Path> backups;
        try (Stream<Path> files = Files.list(folder)) {
            backups = files.filter(file -> file.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString()).reversed())
                    .toList();
        }
        for (Path old : backups.subList(Math.min(KEEP, backups.size()), backups.size())) {
            Files.deleteIfExists(old);
        }
    }
}
