package de.etiennebader.civ.server;

import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.save.SaveListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivServerTest {

    private static final ServerSettings SHORT_DAYS = new ServerSettings(5, 9, 4, CurrencyId.EMERALD);

    private final SeededIdGenerator ids = new SeededIdGenerator(11);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));

    @Test
    @DisplayName("Nach der eingestellten Anzahl Ticks endet ein Tag und wird abgeschlossen")
    void tickCompletesDay() {
        CivServer server = CivServer.start(new MemoryStateStore(), SHORT_DAYS, ids, SaveListener.NONE);

        List<Optional<DayReport>> reports = List.of(server.tick(), server.tick(), server.tick(), server.tick(), server.tick());

        assertTrue(reports.subList(0, 4).stream().allMatch(Optional::isEmpty));
        assertEquals(Day.of(1), reports.get(4).orElseThrow().day());
        assertEquals(Day.of(2), server.calendar().today());
    }

    @Test
    @DisplayName("Speichern läuft im Hintergrund; nach dem Stoppen ist alles in der Datenbank und lässt sich wieder laden")
    void savesInBackgroundAndRestores() throws IOException {
        Path file = Files.createTempDirectory("civ-server").resolve("civ.sqlite");
        RecordingListener listener = new RecordingListener(1);
        CivServer server = CivServer.open(file, SHORT_DAYS, listener);
        server.playerJoined(alice, "Alice");
        server.commands().found(caller(0, 0), "Nordhain");

        assertTrue(server.save());
        assertTrue(listener.awaitSaves());
        assertNotEquals(Thread.currentThread(), listener.savingThreads.getFirst());
        server.tick();
        server.commands().claim(caller(1, 0), ClaimType.CORE);
        FlushReport last = server.stop();

        assertEquals(1, last.chunks());
        CivServer reopened = CivServer.open(file, SHORT_DAYS, SaveListener.NONE);
        assertEquals("Nordhain", reopened.queries().municipalityOf(alice).orElseThrow().name().value());
        assertEquals(2, reopened.queries().territory().territoryOf(reopened.queries().polities().getFirst().id()).size());
        assertEquals(Optional.of("Alice"), reopened.players().nameOf(alice));
        assertEquals(1, reopened.calendar().state().ticksIntoDay());
        reopened.stop();
    }

    @Test
    @DisplayName("Ein fehlgeschlagenes Speichern geht nicht verloren, sondern wird beim nächsten Mal nachgeholt")
    void failedBackgroundSaveIsRetried() {
        MemoryStateStore store = new MemoryStateStore();
        RecordingListener listener = new RecordingListener(1);
        CivServer server = CivServer.start(store, SHORT_DAYS, ids, listener);
        store.failNext(1);
        server.commands().found(caller(0, 0), "Nordhain");

        server.save();
        assertTrue(listener.awaitFailure());
        assertTrue(server.save());
        assertTrue(listener.awaitSaves());

        assertEquals(1, store.written.size());
        assertEquals(1, store.written.getFirst().polities().size());
        assertEquals(2, store.written.getFirst().events().size());
        server.stop();
    }

    @Test
    @DisplayName("Ohne Änderungen seit dem Laden oder letzten Speichern wird nichts gespeichert")
    void saveWithoutChangesDoesNothing() {
        CivServer server = CivServer.start(new MemoryStateStore(), SHORT_DAYS, ids, SaveListener.NONE);

        assertFalse(server.save());
        server.tick();
        assertTrue(server.save());
        assertFalse(server.save());
        server.stop();
    }

    @Test
    @DisplayName("Nach dem Stoppen ist der Server nicht mehr verwendbar")
    void stoppedServerRejectsCalls() {
        CivServer server = CivServer.start(new MemoryStateStore(), SHORT_DAYS, ids, SaveListener.NONE);
        server.stop();

        assertFalse(server.isRunning());
        assertThrows(IllegalStateException.class, server::tick);
        assertThrows(IllegalStateException.class, server::save);
        assertThrows(IllegalStateException.class, server::stop);
    }

    private Caller caller(int chunkX, int chunkZ) {
        return new Caller(alice, "Alice", new ChunkCoord(DimensionId.OVERWORLD, chunkX, chunkZ).center(70));
    }

    /** Zählt Speicherergebnisse und merkt sich, in welchem Thread gespeichert wurde. */
    private static final class RecordingListener implements SaveListener {

        private final CountDownLatch saves;
        private final CountDownLatch failures = new CountDownLatch(1);
        private final List<Thread> savingThreads = new CopyOnWriteArrayList<>();

        RecordingListener(int expectedSaves) {
            this.saves = new CountDownLatch(expectedSaves);
        }

        @Override
        public void saved(FlushReport report, Duration took) {
            savingThreads.add(Thread.currentThread());
            saves.countDown();
        }

        @Override
        public void failed(RuntimeException error) {
            failures.countDown();
        }

        boolean awaitSaves() {
            return await(saves);
        }

        boolean awaitFailure() {
            return await(failures);
        }

        private static boolean await(CountDownLatch latch) {
            try {
                return latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
