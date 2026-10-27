package de.etiennebader.civ.server;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.persistence.ChangeTracker;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.application.persistence.StateStore;
import de.etiennebader.civ.application.persistence.WorldPersistence;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.RandomIdGenerator;
import de.etiennebader.civ.persistence.store.SqliteStateStore;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.protection.ProtectionService;
import de.etiennebader.civ.server.save.AsyncSaver;
import de.etiennebader.civ.server.save.SaveListener;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine laufende Zivilisation auf einem Server: vom Laden beim Start über Tagestakt und Speichern
 * bis zum Herunterfahren. Kennt kein Minecraft; {@code civ-fabric} ruft die Methoden aus den
 * passenden Spielereignissen auf.
 *
 * <p>Alle Methoden laufen auf dem Spielthread. Nur das Schreiben der Speicherstände geschieht
 * in einem eigenen Thread.
 */
public final class CivServer {

    /** So lange wartet {@link #stop()} höchstens auf ausstehende Speicherstände. */
    static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final CivApplication app;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final WorldPersistence persistence;
    private final AsyncSaver saver;
    private final AutoCloseable storage;
    private final CivCommands commands;
    private final ProtectionService protection;
    private boolean running = true;

    private CivServer(StateStore store, AutoCloseable storage, ServerSettings settings, IdGenerator ids,
                      SaveListener listener) {
        Repositories repositories = InMemoryRepositories.create();
        ChangeTracker tracker = new ChangeTracker();
        this.storage = storage;
        this.calendar = new Calendar(settings.ticksPerDay(), CalendarState.START);
        this.players = new PlayerDirectory();
        this.app = new CivApplication(repositories, ids, settings.claimPolicy(), tracker);
        this.persistence = new WorldPersistence(repositories, store, tracker, calendar, players);
        this.persistence.restore();
        this.saver = new AsyncSaver(persistence, listener);
        Texts texts = new Texts(app.queries(), players);
        this.protection = new ProtectionService(app.queries(), texts);
        this.commands = new CivCommands(app, calendar, settings.currency(), texts, protection);
    }

    /**
     * Öffnet die Datenbank (legt sie bei Bedarf an), bringt das Schema auf den neuesten Stand und lädt den Zustand.
     *
     * @throws de.etiennebader.civ.persistence.PersistenceException wenn die Datei nicht geöffnet oder gelesen werden kann
     */
    public static CivServer open(Path databaseFile, ServerSettings settings, SaveListener listener) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        SqliteStateStore store = SqliteStateStore.open(databaseFile);
        try {
            return new CivServer(store, store, settings, new RandomIdGenerator(), listener);
        } catch (RuntimeException e) {
            store.close();
            throw e;
        }
    }

    /** Für Tests: mit beliebigem Speicher und vorhersagbaren IDs. */
    static CivServer start(StateStore store, ServerSettings settings, IdGenerator ids, SaveListener listener) {
        return new CivServer(store, () -> {
        }, settings, ids, listener);
    }

    /**
     * Einmal pro Spieltick aufrufen. Endet dabei ein Tag, wird er abgeschlossen (Grundsteuer usw.).
     *
     * @return der Bericht des gerade beendeten Tages, falls einer endete
     */
    public Optional<DayReport> tick() {
        requireRunning();
        return calendar.tick().map(day -> app.endOfDay().execute(day));
    }

    /**
     * Zusammen mit der Weltspeicherung aufrufen: erfasst alle Änderungen und schreibt sie im Hintergrund.
     * Zuvor fehlgeschlagene Speicherstände werden dabei erneut berücksichtigt.
     *
     * @return ob es etwas zu speichern gab
     */
    public boolean save() {
        requireRunning();
        saver.requeueFailures();
        if (!persistence.hasChanges()) {
            return false;
        }
        saver.submit(persistence.prepareSave());
        return true;
    }

    /** Merkt sich den Namen eines Spielers, der den Server betritt. */
    public void playerJoined(PlayerRef player, String name) {
        requireRunning();
        players.record(player, name);
    }

    /**
     * Wartet auf ausstehende Speicherstände, schreibt alle restlichen Änderungen synchron und
     * schließt die Datenbank. Danach ist die Instanz nicht mehr verwendbar.
     *
     * @return was zuletzt synchron geschrieben wurde
     * @throws RuntimeException wenn das letzte Speichern fehlschlägt; die Datenbank wird trotzdem geschlossen
     */
    public FlushReport stop() {
        requireRunning();
        running = false;
        try {
            saver.finish(SHUTDOWN_TIMEOUT);
            saver.requeueFailures();
            return persistence.flush();
        } finally {
            saver.close();
            closeStorage();
        }
    }

    private void closeStorage() {
        try {
            storage.close();
        } catch (Exception e) {
            throw new IllegalStateException("Datenbank konnte nicht geschlossen werden", e);
        }
    }

    public CivCommands commands() {
        return commands;
    }

    /** Gebietsschutz; für jede Handlung eines Spielers an einem Block aufrufen. */
    public ProtectionService protection() {
        return protection;
    }

    public WorldQueries queries() {
        return app.queries();
    }

    public Calendar calendar() {
        return calendar;
    }

    public PlayerDirectory players() {
        return players;
    }

    /** Für Tests und spätere Etappen, die weitere Use Cases brauchen. */
    CivApplication app() {
        return app;
    }

    public boolean isRunning() {
        return running;
    }

    private void requireRunning() {
        if (!running) {
            throw new IllegalStateException("Der CivServer wurde bereits gestoppt");
        }
    }
}
