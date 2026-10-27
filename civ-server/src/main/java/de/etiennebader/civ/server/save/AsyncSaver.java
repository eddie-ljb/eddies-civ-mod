package de.etiennebader.civ.server.save;

import de.etiennebader.civ.application.persistence.PendingSave;
import de.etiennebader.civ.application.persistence.WorldPersistence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Schreibt erfasste Speicherstände in einem eigenen Thread, damit der Spielthread nicht auf die
 * Festplatte wartet.
 *
 * <p>Die Stände werden nacheinander in der Reihenfolge ihrer Erfassung geschrieben. Ein
 * fehlgeschlagener Stand wird vorgemerkt und beim nächsten {@link #requeueFailures()} auf dem
 * Spielthread an {@link WorldPersistence} zurückgegeben, damit ihn der nächste Speichervorgang
 * mit dem dann aktuellen Stand erneut schreibt.
 */
public final class AsyncSaver implements AutoCloseable {

    static final String THREAD_NAME = "eddies-civ-save";

    private final WorldPersistence persistence;
    private final SaveListener listener;
    private final ExecutorService executor;
    private final Queue<PendingSave> failed = new ConcurrentLinkedQueue<>();

    public AsyncSaver(WorldPersistence persistence, SaveListener listener) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Stellt einen Speicherstand zum Schreiben ein und kehrt sofort zurück. */
    public void submit(PendingSave save) {
        Objects.requireNonNull(save, "save");
        executor.execute(() -> write(save));
    }

    private void write(PendingSave save) {
        long start = System.nanoTime();
        try {
            persistence.write(save);
        } catch (RuntimeException e) {
            failed.add(save);
            listener.failed(e);
            return;
        }
        listener.saved(save.report(), Duration.ofNanos(System.nanoTime() - start));
    }

    /**
     * Gibt fehlgeschlagene Stände an {@link WorldPersistence} zurück. Nur auf dem Spielthread aufrufen.
     * Neuere zuerst, damit die Events der älteren am Ende wieder vorn stehen.
     */
    public void requeueFailures() {
        List<PendingSave> failures = new ArrayList<>();
        for (PendingSave save = failed.poll(); save != null; save = failed.poll()) {
            failures.add(save);
        }
        failures.reversed().forEach(persistence::requeue);
    }

    /**
     * Nimmt nichts mehr an und wartet, bis alle eingestellten Stände geschrieben sind.
     *
     * @return ob alles rechtzeitig geschrieben wurde
     */
    public boolean finish(Duration timeout) {
        executor.shutdown();
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
