package de.etiennebader.civ.server.federation;

import de.etiennebader.civ.domain.polity.Polity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Welche Ebenen es gibt: immer Gemeinde, Land und Nation, dazu eigene aus der Konfiguration,
 * z. B. {@code kreis:Kreis:10,bund:Bund:40}. Der Rang ordnet sie; ein Bund muss höher stehen als jedes seiner Glieder.
 */
public final class Levels {

    public static final Level MUNICIPALITY = new Level(Polity.MUNICIPAL_LEVEL, "", 0);
    public static final Level STATE = new Level("land", "", 20);
    public static final Level NATION = new Level("nation", "", 30);
    public static final Levels DEFAULT = new Levels(List.of());
    /** Rang einer Ebene, die nicht mehr konfiguriert ist: zwischen Land und Nation. */
    static final int UNKNOWN_RANK = 25;

    private final Map<String, Level> byId = new LinkedHashMap<>();
    private final List<Level> custom;

    public Levels(List<Level> custom) {
        this.custom = List.copyOf(custom);
        for (Level level : List.of(MUNICIPALITY, STATE, NATION)) {
            byId.put(level.id(), level);
        }
        for (Level level : this.custom) {
            if (byId.containsKey(level.id())) {
                throw new IllegalArgumentException("Ebene doppelt: " + level.id());
            }
            if (level.rank() <= MUNICIPALITY.rank()) {
                throw new IllegalArgumentException("Eigene Ebenen stehen über der Gemeinde: " + level.id());
            }
            byId.put(level.id(), level);
        }
    }

    /**
     * Liest {@code id:Name:Rang} durch Kommas getrennt. Fehlerhafte Einträge werden übersprungen.
     */
    public static Levels parse(String spec) {
        List<Level> custom = new ArrayList<>();
        for (String entry : spec.split(",")) {
            String[] parts = entry.strip().split(":");
            if (parts.length != 3) {
                continue;
            }
            try {
                Level level = new Level(parts[0].strip().toLowerCase(Locale.ROOT), parts[1].strip(),
                        Integer.parseInt(parts[2].strip()));
                if (!level.name().isEmpty() && level.rank() > 0
                        && custom.stream().noneMatch(other -> other.id().equals(level.id()))
                        && !List.of(MUNICIPALITY.id(), STATE.id(), NATION.id()).contains(level.id())) {
                    custom.add(level);
                }
            } catch (IllegalArgumentException e) {
                // fehlerhafter Eintrag: überspringen
            }
        }
        return new Levels(custom);
    }

    /** Die Form, in der {@link #parse} die eigenen Ebenen liest. */
    public String spec() {
        return String.join(",", custom.stream().map(level -> level.id() + ":" + level.name() + ":" + level.rank())
                .toList());
    }

    /** Alle Ebenen nach Rang. */
    public List<Level> all() {
        return byId.values().stream().sorted(Comparator.comparingInt(Level::rank)).toList();
    }

    /** Ebenen, auf denen Bünde gegründet werden können. */
    public List<Level> federal() {
        return all().stream().filter(level -> level.rank() > 0).toList();
    }

    /** Sucht nach Kennung oder (eigenem) Namen, ohne Groß- und Kleinschreibung. */
    public Optional<Level> find(String input) {
        String wanted = input.strip().toLowerCase(Locale.ROOT);
        Level byKey = byId.get(wanted);
        if (byKey != null) {
            return Optional.of(byKey);
        }
        return byId.values().stream().filter(level -> level.name().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    /** Die Ebene zu einer gespeicherten Kennung; eine nicht mehr konfigurierte Ebene behält ihre Kennung als Namen. */
    public Level of(String id) {
        Level level = byId.get(id);
        return level != null ? level : new Level(id, id, UNKNOWN_RANK);
    }

    public int rankOf(String id) {
        return of(id).rank();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Levels levels && levels.custom.equals(custom);
    }

    @Override
    public int hashCode() {
        return custom.hashCode();
    }

    @Override
    public String toString() {
        return "Levels[" + spec() + "]";
    }
}
