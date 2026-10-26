package de.etiennebader.civ.persistence.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eine Schemaänderung: Version, Beschreibung und die SQL-Anweisungen.
 *
 * <p>Dateinamen folgen dem Muster {@code V001__beschreibung.sql}. Anweisungen enden mit einem
 * Semikolon am Zeilenende; Zeilen, die mit {@code --} beginnen, sind Kommentare.
 */
public record Migration(int version, String description, List<String> statements) {

    private static final Pattern FILE_NAME = Pattern.compile("V(\\d{3})__([a-z0-9_]+)\\.sql");
    private static final String RESOURCE_FOLDER = "/db/migration/";

    public Migration {
        if (version < 1) {
            throw new IllegalArgumentException("Version muss mindestens 1 sein: " + version);
        }
        Objects.requireNonNull(description, "description");
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("Migration " + version + " enthält keine Anweisungen");
        }
    }

    /** Lädt eine Migration aus {@code /db/migration/<fileName>} im Klassenpfad. */
    public static Migration fromResource(String fileName) {
        Matcher matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Ungültiger Migrationsname: " + fileName);
        }
        try (InputStream stream = Migration.class.getResourceAsStream(RESOURCE_FOLDER + fileName)) {
            if (stream == null) {
                throw new IllegalArgumentException("Migration fehlt im Klassenpfad: " + fileName);
            }
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new Migration(Integer.parseInt(matcher.group(1)), matcher.group(2).replace('_', ' '), split(sql));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Zerlegt ein Skript in einzelne Anweisungen. */
    static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().strip();
                statements.add(statement.substring(0, statement.length() - 1).strip());
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            throw new IllegalArgumentException("Anweisung ohne abschließendes Semikolon: " + current.toString().strip());
        }
        return statements;
    }
}
