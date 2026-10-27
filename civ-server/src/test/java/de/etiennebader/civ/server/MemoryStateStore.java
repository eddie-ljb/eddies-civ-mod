package de.etiennebader.civ.server;

import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.persistence.StateDelta;
import de.etiennebader.civ.application.persistence.StateStore;
import de.etiennebader.civ.application.persistence.StoredWorld;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Ein Speicher für Tests: beginnt leer, merkt sich Geschriebenes und kann gezielt fehlschlagen. */
public final class MemoryStateStore implements StateStore {

    public final List<StateDelta> written = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();

    /** Die nächsten {@code count} Schreibvorgänge schlagen fehl. */
    public void failNext(int count) {
        failuresLeft.set(count);
    }

    @Override
    public StoredWorld load() {
        return new StoredWorld(List.of(), TerritoryRegistry.empty(), List.of(), Ledger.empty(), List.of(),
                CalendarState.START);
    }

    @Override
    public void write(StateDelta delta) {
        if (failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            throw new IllegalStateException("Festplatte voll");
        }
        written.add(delta);
    }
}
