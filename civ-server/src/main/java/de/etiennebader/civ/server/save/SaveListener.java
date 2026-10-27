package de.etiennebader.civ.server.save;

import de.etiennebader.civ.application.persistence.FlushReport;

import java.time.Duration;

/**
 * Erfährt, wie Speichervorgänge ausgehen, z. B. um sie zu protokollieren.
 *
 * <p>Wird aus dem Speicher-Thread aufgerufen, nicht aus dem Spielthread.
 */
public interface SaveListener {

    SaveListener NONE = new SaveListener() {
        @Override
        public void saved(FlushReport report, Duration took) {
        }

        @Override
        public void failed(RuntimeException error) {
        }
    };

    void saved(FlushReport report, Duration took);

    /** Die Änderungen gehen nicht verloren, sondern werden beim nächsten Speichern erneut geschrieben. */
    void failed(RuntimeException error);
}
