package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Polity;

import java.util.Objects;

/**
 * Lädt den Weltzustand beim Start in die Repositories und schreibt Änderungen beim Speichern zurück.
 *
 * <p>Im Spiel wird {@link #flush()} zusammen mit der Weltspeicherung von Minecraft aufgerufen
 * (Autosave und Beenden). Schlägt das Schreiben fehl, bleiben die Änderungen vorgemerkt und
 * werden beim nächsten Mal erneut geschrieben.
 */
public final class WorldPersistence {

    private final Repositories repositories;
    private final StateStore store;
    private final ChangeTracker tracker;
    private final WorldQueries queries;

    /**
     * @param tracker muss als Event-Publisher an dieselbe Application gehängt sein, die diese Repositories nutzt
     */
    public WorldPersistence(Repositories repositories, StateStore store, ChangeTracker tracker) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.store = Objects.requireNonNull(store, "store");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.queries = new WorldQueries(repositories);
    }

    /**
     * Lädt den gespeicherten Zustand in die noch leeren Repositories.
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
    }

    /**
     * Schreibt alle Änderungen seit dem letzten Speichern in einer Transaktion.
     *
     * @return was geschrieben wurde; leer, wenn sich nichts geändert hat
     */
    public FlushReport flush() {
        if (!tracker.hasChanges()) {
            return FlushReport.of(ChangeSet.empty());
        }
        ChangeSet changes = tracker.takeChanges();
        try {
            store.write(changes, queries);
        } catch (RuntimeException e) {
            tracker.requeue(changes);
            throw e;
        }
        return FlushReport.of(changes);
    }
}
