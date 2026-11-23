package de.etiennebader.civ.application.directory;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.population.Gender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Ordnet Spielern ihre Namen zu, damit Ausgaben „Eddie“ statt einer UUID zeigen.
 *
 * <p>Namen sind kein Teil der Domäne: Ein Spieler bleibt über seine UUID derselbe, auch wenn er
 * sich umbenennt. Das Verzeichnis merkt sich geänderte Einträge, damit nur diese gespeichert werden.
 */
public final class PlayerDirectory {

    private final Map<PlayerRef, KnownPlayer> players = new LinkedHashMap<>();
    private final Set<PlayerRef> changed = new LinkedHashSet<>();

    /** Übernimmt gespeicherte Einträge, ohne sie als geändert zu markieren. */
    public void restore(Collection<KnownPlayer> stored) {
        Objects.requireNonNull(stored, "stored");
        stored.forEach(player -> players.put(player.player(), player));
    }

    /** Merkt sich den aktuellen Namen, z. B. beim Betreten des Servers. */
    public void record(PlayerRef player, String name) {
        KnownPlayer known = new KnownPlayer(player, name, genderOf(player));
        if (!known.equals(players.get(player))) {
            players.put(player, known);
            changed.add(player);
        }
    }

    /** Das vom Spieler gewählte Geschlecht; leer, solange er keines angegeben hat. */
    public Optional<Gender> genderOf(PlayerRef player) {
        return Optional.ofNullable(players.get(player)).flatMap(KnownPlayer::gender);
    }

    /** Speichert das gewählte Geschlecht eines bereits bekannten Spielers. */
    public void setGender(PlayerRef player, Gender gender) {
        Objects.requireNonNull(gender, "gender");
        KnownPlayer known = players.get(player);
        if (known == null) {
            throw new IllegalArgumentException("Unbekannter Spieler: " + player);
        }
        KnownPlayer updated = new KnownPlayer(player, known.name(), Optional.of(gender));
        if (!updated.equals(known)) {
            players.put(player, updated);
            changed.add(player);
        }
    }

    public Optional<String> nameOf(PlayerRef player) {
        return Optional.ofNullable(players.get(player)).map(KnownPlayer::name);
    }

    /** Sucht ohne Beachtung der Groß- und Kleinschreibung; bei Namensgleichheit gewinnt der zuletzt Erfasste. */
    public Optional<PlayerRef> findByName(String name) {
        Objects.requireNonNull(name, "name");
        String wanted = name.toLowerCase(Locale.ROOT);
        PlayerRef found = null;
        for (KnownPlayer player : players.values()) {
            if (player.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                found = player.player();
            }
        }
        return Optional.ofNullable(found);
    }

    public List<KnownPlayer> all() {
        return List.copyOf(players.values());
    }

    public boolean hasChanges() {
        return !changed.isEmpty();
    }

    /** Liefert die seit dem letzten Aufruf geänderten Einträge und beginnt von vorn. */
    public List<KnownPlayer> takeChanges() {
        List<KnownPlayer> result = new ArrayList<>();
        changed.forEach(player -> result.add(players.get(player)));
        changed.clear();
        return result;
    }

    /** Markiert Einträge erneut als geändert, deren Speichern fehlgeschlagen ist. */
    public void requeue(Collection<KnownPlayer> failed) {
        Objects.requireNonNull(failed, "failed");
        failed.forEach(player -> changed.add(player.player()));
    }
}
