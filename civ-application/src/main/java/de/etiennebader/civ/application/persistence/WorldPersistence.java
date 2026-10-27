package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Polity;

import java.util.Objects;

/**
 * Lädt den Weltzustand beim Start in die Repositories und schreibt Änderungen beim Speichern zurück.
 *
 * <p>Speichern geht in zwei Schritten: {@link #prepareSave()} erfasst auf dem Spielthread eine
 * unveränderliche Kopie aller Änderungen, die der {@link StateStore} danach auch in einem anderen
 * Thread schreiben kann. Schlägt das Schreiben fehl, gibt {@link #requeue} die Änderungen zurück;
 * sie werden beim nächsten Mal erneut erfasst. {@link #flush()} erledigt beides synchron.
 */
public final class WorldPersistence {

    private final Repositories repositories;
    private final StateStore store;
    private final ChangeTracker tracker;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final WorldQueries queries;
    private CalendarState savedCalendar;

    /**
     * @param tracker muss als Event-Publisher an dieselbe Application gehängt sein, die diese Repositories nutzt
     */
    public WorldPersistence(Repositories repositories, StateStore store, ChangeTracker tracker,
                            Calendar calendar, PlayerDirectory players) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.store = Objects.requireNonNull(store, "store");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.queries = new WorldQueries(repositories);
    }

    /**
     * Lädt den gespeicherten Zustand in die noch leeren Repositories, das Spielerverzeichnis und den Kalender.
     *
     * @throws IllegalStateException wenn die Repositories bereits Polities enthalten
     */
    public void restore() {
        if (!repositories.polities().findAll().isEmpty()) {
            throw new IllegalStateException("Laden ist nur in leere Repositories möglich");
        }
        StoredWorld world = store.load();
        world.polities().forEach(polity -> repositories.polities().save(polity));
        world.land().forEach(land -> repositories.land().save(land));
        repositories.territory().save(world.territory());
        repositories.ledger().save(world.ledger());
        for (Polity polity : world.polities()) {
            if (repositories.land().findByPolity(polity.id()).isEmpty()) {
                repositories.land().save(LandRegistry.open(polity.id()));
            }
        }
        players.restore(world.players());
        calendar.restore(world.calendar());
        savedCalendar = calendar.state();
    }

    /** Ob seit dem letzten Speichern etwas passiert ist, auch nur im Kalender. */
    public boolean hasChanges() {
        return tracker.hasChanges() || players.hasChanges() || !calendar.state().equals(savedCalendar);
    }

    /**
     * Erfasst alle Änderungen seit dem letzten Speichern. Muss auf dem Thread laufen, der den
     * Arbeitsstand verändert. Der Kalender wird immer mitgeschrieben.
     */
    public PendingSave prepareSave() {
        ChangeSet changes = tracker.takeChanges();
        savedCalendar = calendar.state();
        StateDelta delta = StateDelta.capture(changes, queries, players.takeChanges(), savedCalendar);
        return new PendingSave(changes, delta);
    }

    /** Gibt einen fehlgeschlagenen Speicherstand zurück, damit er beim nächsten Mal erneut geschrieben wird. */
    public void requeue(PendingSave failed) {
        Objects.requireNonNull(failed, "failed");
        tracker.requeue(failed.changes());
        players.requeue(failed.delta().players());
        savedCalendar = null;
    }

    /**
     * Schreibt alle Änderungen seit dem letzten Speichern synchron in einer Transaktion.
     * Bei einem Fehler bleiben die Änderungen vorgemerkt.
     *
     * @return was geschrieben wurde; leer, wenn sich nichts geändert hat
     */
    public FlushReport flush() {
        if (!hasChanges()) {
            return FlushReport.of(ChangeSet.empty());
        }
        PendingSave save = prepareSave();
        try {
            write(save);
        } catch (RuntimeException e) {
            requeue(save);
            throw e;
        }
        return save.report();
    }

    /**
     * Schreibt einen erfassten Speicherstand. Darf in einem anderen Thread laufen als {@link #prepareSave()}.
     * Bei einem Fehler muss der Aufrufer den Stand auf dem Spielthread über {@link #requeue} zurückgeben.
     */
    public void write(PendingSave save) {
        store.write(Objects.requireNonNull(save, "save").delta());
    }
}
