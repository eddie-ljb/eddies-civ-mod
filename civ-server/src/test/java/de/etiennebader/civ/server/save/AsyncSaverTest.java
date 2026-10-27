package de.etiennebader.civ.server.save;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.persistence.ChangeTracker;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.application.persistence.WorldPersistence;
import de.etiennebader.civ.server.MemoryStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncSaverTest {

    private final MemoryStateStore store = new MemoryStateStore();
    private final Calendar calendar = new Calendar(100, CalendarState.START);
    private final Repositories repositories = InMemoryRepositories.create();
    private final WorldPersistence persistence = new WorldPersistence(repositories, store, new ChangeTracker(),
            calendar, new PlayerDirectory());

    @Test
    @DisplayName("Speicherstände werden in einem eigenen Thread in Erfassungsreihenfolge geschrieben")
    void writesInOrderOnOwnThread() {
        List<String> threads = new CopyOnWriteArrayList<>();
        AsyncSaver saver = new AsyncSaver(persistence, listener(threads));

        for (int i = 0; i < 5; i++) {
            calendar.tick();
            saver.submit(persistence.prepareSave());
        }

        assertTrue(saver.finish(Duration.ofSeconds(5)));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), store.written.stream().map(delta -> delta.calendar().ticksIntoDay()).toList());
        assertTrue(threads.stream().allMatch(AsyncSaver.THREAD_NAME::equals));
    }

    private static SaveListener listener(List<String> threads) {
        return new SaveListener() {
            @Override
            public void saved(FlushReport report, Duration took) {
                threads.add(Thread.currentThread().getName());
            }

            @Override
            public void failed(RuntimeException error) {
                threads.add("fehler");
            }
        };
    }
}
