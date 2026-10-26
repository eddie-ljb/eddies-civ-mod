package de.etiennebader.civ.application.persistence;

/**
 * Was beim Speichern geschrieben wurde, z. B. für ein Log.
 */
public record FlushReport(int polities, int chunks, int plots, int accounts, int events) {

    public static FlushReport of(ChangeSet changes) {
        return new FlushReport(changes.polities().size(), changes.chunks().size(), changes.plots().size(),
                changes.accounts().size(), changes.events().size());
    }

    public boolean isEmpty() {
        return polities == 0 && chunks == 0 && plots == 0 && accounts == 0 && events == 0;
    }
}
