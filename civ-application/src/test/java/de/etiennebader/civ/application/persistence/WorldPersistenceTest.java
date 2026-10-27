package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.application.territory.ReleaseChunk;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityFounded;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolitySnapshot;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.chunk;
import static de.etiennebader.civ.application.ApplicationFixture.seat;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPersistenceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(8);
    private final ChangeTracker tracker = new ChangeTracker();
    private final Repositories repositories = InMemoryRepositories.create();
    private final CivApplication app = new CivApplication(repositories, ids, CompositeClaimPolicy.standard(4, 1), tracker);
    private final RecordingStore store = new RecordingStore();
    private final Calendar calendar = Calendar.standard();
    private final PlayerDirectory players = new PlayerDirectory();
    private final WorldPersistence persistence = new WorldPersistence(repositories, store, tracker, calendar, players);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));

    @Test
    @DisplayName("Speichern schreibt alle Änderungen einmal und meldet, was geschrieben wurde")
    void flushWritesChangesOnce() {
        app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, seat(0, 0), DAY));

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
        app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, seat(0, 0), DAY));

        assertThrows(IllegalStateException.class, persistence::restore);
    }

    @Test
    @DisplayName("Die erfasste Kopie ändert sich nicht, wenn der Arbeitsstand danach weiterläuft")
    void preparedSaveIsDetachedFromWorkingState() {
        PolityId polity = app.foundPolity().execute(
                new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, seat(0, 0), DAY));
        PendingSave save = persistence.prepareSave();

        app.joinPolity().execute(new JoinPolity.Command(polity, bob, MembershipType.CITIZEN, DAY));

        PolitySnapshot captured = save.delta().polities().getFirst().polity().orElseThrow();
        assertEquals(1, captured.members().size());
    }

    @Test
    @DisplayName("Ein zurückgegebener Speicherstand wird beim nächsten Mal mit dem neuesten Stand geschrieben")
    void requeuedSaveIsWrittenWithLatestState() {
        PolityId polity = app.foundPolity().execute(
                new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, seat(0, 0), DAY));
        players.record(alice, "Alice");
        PendingSave failed = persistence.prepareSave();
        app.joinPolity().execute(new JoinPolity.Command(polity, bob, MembershipType.CITIZEN, DAY));

        persistence.requeue(failed);
        persistence.flush();

        StateDelta delta = store.written.getFirst();
        assertEquals(2, delta.polities().getFirst().polity().orElseThrow().members().size());
        assertEquals(List.of(new KnownPlayer(alice, "Alice")), delta.players());
        assertEquals(3, delta.events().size());
        assertEquals(PolityFounded.class, delta.events().getFirst().getClass());
    }

    @Test
    @DisplayName("Ein fortgeschrittener Kalender gilt als Änderung und wird mitgeschrieben")
    void calendarProgressIsSaved() {
        persistence.flush();
        assertFalse(persistence.hasChanges());

        calendar.tick();

        assertTrue(persistence.hasChanges());
        persistence.flush();
        assertEquals(calendar.state(), store.written.getLast().calendar());
    }

    @Test
    @DisplayName("Gelöschte Einträge erscheinen als leere Werte")
    void releasedChunkIsCapturedAsEmpty() {
        PolityId polity = app.foundPolity().execute(
                new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, seat(0, 0), DAY));
        app.claimChunk().execute(new ClaimChunk.Command(polity, chunk(1, 0), ClaimType.CORE, DAY));
        persistence.flush();

        app.releaseChunk().execute(new ReleaseChunk.Command(polity, chunk(1, 0), DAY));
        persistence.flush();

        assertEquals(List.of(new StateDelta.ClaimChange(chunk(1, 0), Optional.empty())), store.written.getLast().claims());
    }

    /** Merkt sich geschriebene Änderungen; kann einmalig fehlschlagen. */
    private static final class RecordingStore implements StateStore {

        private final List<StateDelta> written = new ArrayList<>();
        private boolean failNext;

        @Override
        public StoredWorld load() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(StateDelta delta) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Speicher nicht erreichbar");
            }
            written.add(delta);
        }
    }
}
