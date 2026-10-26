package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.chunk;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPersistenceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(8);
    private final ChangeTracker tracker = new ChangeTracker();
    private final Repositories repositories = InMemoryRepositories.create();
    private final CivApplication app = new CivApplication(repositories, ids, CompositeClaimPolicy.standard(4, 1), tracker);
    private final RecordingStore store = new RecordingStore();
    private final WorldPersistence persistence = new WorldPersistence(repositories, store, tracker);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));

    @Test
    @DisplayName("Speichern schreibt alle Änderungen einmal und meldet, was geschrieben wurde")
    void flushWritesChangesOnce() {
        app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, chunk(0, 0), DAY));

        FlushReport report = persistence.flush();

        assertEquals(new FlushReport(1, 1, 0, 0, 2), report);
        assertEquals(1, store.written.size());
        assertTrue(persistence.flush().isEmpty());
        assertEquals(1, store.written.size());
    }

    @Test
    @DisplayName("Schlägt das Schreiben fehl, bleiben die Änderungen für den nächsten Versuch erhalten")
    void failedFlushKeepsChanges() {
        app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(5), DAY));
        store.failNext = true;

        assertThrows(IllegalStateException.class, persistence::flush);
        FlushReport retry = persistence.flush();

        assertEquals(1, retry.accounts());
        assertEquals(1, retry.events());
    }

    @Test
    @DisplayName("Laden ist nur in leere Repositories erlaubt")
    void restoreRequiresEmptyRepositories() {
        app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, chunk(0, 0), DAY));

        assertThrows(IllegalStateException.class, persistence::restore);
    }

    /** Merkt sich geschriebene Änderungen; kann einmalig fehlschlagen. */
    private static final class RecordingStore implements StateStore {

        private final List<ChangeSet> written = new ArrayList<>();
        private boolean failNext;

        @Override
        public StoredWorld load() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(ChangeSet changes, WorldQueries current) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Speicher nicht erreichbar");
            }
            written.add(changes);
        }
    }
}
