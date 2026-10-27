package de.etiennebader.civ.application.persistence;

import java.util.Objects;

/**
 * Ein erfasster, noch nicht geschriebener Speicherstand.
 *
 * @param changes was sich geändert hat; wird bei einem Fehler über {@link WorldPersistence#requeue} zurückgegeben
 * @param delta   die zu schreibenden Werte
 */
public record PendingSave(ChangeSet changes, StateDelta delta) {

    public PendingSave {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(delta, "delta");
    }

    public FlushReport report() {
        return FlushReport.of(changes);
    }
}
