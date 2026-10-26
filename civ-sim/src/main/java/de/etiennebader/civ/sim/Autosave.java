package de.etiennebader.civ.sim;

import de.etiennebader.civ.application.persistence.StateStore;

import java.util.Objects;

/**
 * Speichert den Zustand während der Simulation regelmäßig, wie es Minecraft beim Autosave tut,
 * und am Ende des Laufs.
 *
 * @param store        Ziel, z. B. eine SQLite-Datei
 * @param intervalDays alle wie viele Tage gespeichert wird
 */
public record Autosave(StateStore store, int intervalDays) {

    public Autosave {
        Objects.requireNonNull(store, "store");
        if (intervalDays < 1) {
            throw new IllegalArgumentException("intervalDays muss mindestens 1 sein: " + intervalDays);
        }
    }
}
