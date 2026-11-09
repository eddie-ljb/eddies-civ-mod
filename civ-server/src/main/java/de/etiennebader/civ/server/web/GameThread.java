package de.etiennebader.civ.server.web;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Führt Aufgaben der Webthreads auf dem Spielthread aus. Der Spielthread ruft {@link #runPending()} in jedem Tick auf.
 */
public final class GameThread {

    private final Queue<FutureTask<?>> pending = new ConcurrentLinkedQueue<>();
    private final long timeoutMillis;

    public GameThread(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Wartet, bis der Spielthread die Aufgabe ausgeführt hat.
     *
     * @throws IllegalStateException wenn der Spielthread nicht rechtzeitig antwortet oder die Aufgabe scheitert
     */
    public <T> T call(Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(Objects.requireNonNull(task, "task"));
        pending.add(future);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(false);
            throw new IllegalStateException("Der Spielserver antwortet nicht", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unterbrochen", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Aufgabe fehlgeschlagen", e.getCause());
        }
    }

    /** Auf dem Spielthread aufrufen. */
    public void runPending() {
        FutureTask<?> task;
        while ((task = pending.poll()) != null) {
            task.run();
        }
    }
}
