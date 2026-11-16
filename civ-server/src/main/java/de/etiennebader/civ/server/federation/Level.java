package de.etiennebader.civ.server.federation;

import de.etiennebader.civ.server.command.Message;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Eine politische Ebene wie Gemeinde, Kreis, Land oder Nation.
 *
 * @param id   Kennung, die auch in der Datenbank steht, z. B. {@code land}
 * @param name Anzeigename eigener Ebenen; bei eingebauten Ebenen leer, sie werden übersetzt
 * @param rank Stellung in der Hierarchie: Ein Bund steht immer höher als seine Glieder
 */
public record Level(String id, String name, int rank) {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final String PREFIX = "eddies-civ-mod.level.";

    public Level {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Ungültige Ebene: " + id);
        }
        if (rank < 0 || rank > 1000) {
            throw new IllegalArgumentException("Rang muss zwischen 0 und 1000 liegen: " + rank);
        }
    }

    public boolean builtIn() {
        return name.isEmpty();
    }

    /** Übersetzbarer Name eingebauter Ebenen, sonst der eingetragene Name. */
    public Object displayName() {
        return builtIn() ? Message.of(translationKey(id)) : name;
    }

    public static String translationKey(String id) {
        return PREFIX + id;
    }
}
