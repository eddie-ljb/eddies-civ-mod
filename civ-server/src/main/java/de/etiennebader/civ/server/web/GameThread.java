package de.etiennebader.civ.server.web;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Führt Aufgaben der Webthreads auf dem Spielthread aus. Der Spielthread ruft {@link #runPending()} in jedem Tick auf.
 *
 * <p>Ein pausierter Einzelspieler-Server tickt nicht, arbeitet aber weiter Aufgaben ab, die ihm übergeben werden. Dafür
 * nimmt {@link #wakeVia(Executor)} den Executor des Servers: jede neue Aufgabe stößt dann zusätzlich einen
 * {@link #runPending()} auf dem Spielthread an.
 */
public final class GameThread {

    private final Queue<FutureTask<?>> pending = new ConcurrentLinkedQueue<>();
    private final long timeoutMillis;
    private volatile Executor wake = command -> { };

    public GameThread(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /** Executor, der Aufgaben auf dem Spielthread ausführt, auch wenn der Server pausiert (z. B. {@code server::execute}). */
    public void wakeVia(Executor executor) {
        this.wake = Objects.requireNonNull(executor, "executor");
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
            wake.execute(this::runPending);
        } catch (RuntimeException e) {
            // Der Server nimmt keine Aufgaben mehr an (Herunterfahren); dann bleibt nur der nächste Tick.
        }
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
