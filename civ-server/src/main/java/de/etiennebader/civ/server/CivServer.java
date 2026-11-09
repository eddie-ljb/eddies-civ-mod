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
import de.etiennebader.civ.application.support.ClaimWeighting;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.RandomIdGenerator;
import de.etiennebader.civ.persistence.store.SqliteStateStore;
import de.etiennebader.civ.server.bank.BankService;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.market.ItemTraits;
import de.etiennebader.civ.server.market.MarketService;
import de.etiennebader.civ.server.plot.PlotService;
import de.etiennebader.civ.server.protection.ProtectionService;
import de.etiennebader.civ.server.save.AsyncSaver;
import de.etiennebader.civ.server.save.SaveListener;
import de.etiennebader.civ.server.shop.ShopService;
import de.etiennebader.civ.server.territory.TerritoryWatcher;
import de.etiennebader.civ.server.town.TownHallService;
import de.etiennebader.civ.server.town.TownService;
import de.etiennebader.civ.server.villager.VillagerService;
import de.etiennebader.civ.server.vote.VotingService;
import de.etiennebader.civ.server.web.GameThread;
import de.etiennebader.civ.server.web.MiniHttpServer;
import de.etiennebader.civ.server.web.WebApi;
import de.etiennebader.civ.server.web.WebSessions;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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

    /** So lange wartet eine Anfrage der Webseite höchstens auf den Spielthread. */
    static final long WEB_TIMEOUT_MILLIS = 5_000;

    private final CivApplication app;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final WorldPersistence persistence;
    private final AsyncSaver saver;
    private final AutoCloseable storage;
    private final CivCommands commands;
    private final ProtectionService protection;
    private final BankService bank;
    private final MarketService market;
    private final TerritoryWatcher territory;
    private final TownService towns;
    private final PlotService plots;
    private final VillagerService villagers;
    private final Announcements announcements = new Announcements();
    private final VotingService voting;
    private final TownHallService townHalls;
    private final ShopService shops;
    private final ServerSettings settings;
    private final GameThread gameThread = new GameThread(WEB_TIMEOUT_MILLIS);
    private final WebSessions webSessions = new WebSessions(Clock.systemUTC());
    private MiniHttpServer web;
    private final Texts texts;
    private boolean running = true;

    private CivServer(StateStore store, AutoCloseable storage, ServerSettings settings, IdGenerator ids,
                      SaveListener listener) {
        Repositories repositories = InMemoryRepositories.create();
        ChangeTracker tracker = new ChangeTracker();
        this.storage = storage;
        this.settings = settings;
        this.calendar = new Calendar(settings.ticksPerDay(), CalendarState.START);
        this.players = new PlayerDirectory();
        this.app = new CivApplication(repositories, ids, settings.claimPolicy(), tracker, ClaimWeighting.PERSONS_QUARTER);
        this.persistence = new WorldPersistence(repositories, store, tracker, calendar, players);
        this.persistence.restore();
        this.saver = new AsyncSaver(persistence, listener);
        Texts texts = new Texts(app.queries(), players);
        this.texts = texts;
        this.protection = new ProtectionService(app.queries(), texts);
        this.voting = new VotingService(app, calendar, texts, announcements);
        this.commands = new CivCommands(app, calendar, settings.currency(), texts, protection, voting);
        this.bank = new BankService(app, calendar, settings.currency(), players, texts);
        this.market = new MarketService(app, calendar, settings, players, texts);
        this.territory = new TerritoryWatcher(app.queries(), texts);
        this.towns = new TownService(app.queries(), settings, texts, commands, territory);
        this.plots = new PlotService(app, calendar, players, texts);
        this.villagers = new VillagerService(app, settings, texts, announcements);
        this.townHalls = new TownHallService(app, calendar, texts, announcements);
        this.shops = new ShopService(app, calendar, settings.currency(), texts);
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
        gameThread.runPending();
        return calendar.tick().map(this::closeDay);
    }

    /**
     * Beendet den laufenden Tag sofort, ohne auf die Ticks zu warten: Grundsteuer, Einnahmen der Dorfbewohner und
     * Auszählung fälliger Abstimmungen laufen wie am normalen Tagesende. Für Admins zum Testen.
     *
     * @return der Bericht des beendeten Tages
     */
    public DayReport endDayNow() {
        requireRunning();
        return closeDay(calendar.completeDay());
    }

    private DayReport closeDay(Day day) {
        DayReport report = app.endOfDay().execute(day);
        voting.endOfDay(day);
        villagers.endOfDay(day);
        return report;
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

    /** Vergisst flüchtige Daten eines Spielers, der den Server verlässt. */
    public void playerLeft(PlayerRef player) {
        requireRunning();
        territory.forget(player);
        plots.forget(player);
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
        stopWeb();
        try {
            saver.finish(SHUTDOWN_TIMEOUT);
            saver.requeueFailures();
            return persistence.flush();
        } finally {
            saver.close();
            closeStorage();
        }
    }

    /**
     * Startet die Webseite, falls sie eingeschaltet ist.
     *
     * @param items Eigenschaften eines Items zu seiner ID, für Marktpreise; kommt aus {@code civ-fabric}
     * @return der tatsächliche Port; leer, wenn die Webseite ausgeschaltet ist
     * @throws IOException wenn der Port belegt ist
     */
    public Optional<Integer> startWeb(Function<String, Optional<ItemTraits>> items) throws IOException {
        requireRunning();
        ServerSettings.WebSettings config = settings.web();
        if (!config.enabled() || web != null) {
            return Optional.ofNullable(web).map(MiniHttpServer::port);
        }
        web = MiniHttpServer.start(config.bind(), config.port(), 4, new WebApi(this, gameThread, webSessions, items));
        return Optional.of(web.port());
    }

    private void stopWeb() {
        if (web != null) {
            web.close();
            web = null;
        }
    }

    /** {@code /civ web}: ein Anmeldecode für die Webseite. */
    public Reply webLogin(Caller caller) {
        requireRunning();
        if (web == null) {
            return Reply.failure(Message.of(MessageKey.WEB_DISABLED));
        }
        String code = webSessions.issueCode(caller.player(), caller.name());
        String url = settings.web().publicUrl().isBlank()
                ? "http://<Server-Adresse>:" + web.port()
                : settings.web().publicUrl();
        return Reply.success(Message.of(MessageKey.WEB_CODE, code, url));
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

    public BankService bank() {
        return bank;
    }

    public MarketService market() {
        return market;
    }

    /** Meldet Grenzübertritte; regelmäßig mit der Position jedes Spielers aufrufen. */
    public TerritoryWatcher territory() {
        return territory;
    }

    public TownService towns() {
        return towns;
    }

    public PlotService plots() {
        return plots;
    }

    public VillagerService villagers() {
        return villagers;
    }

    public VotingService voting() {
        return voting;
    }

    public TownHallService townHalls() {
        return townHalls;
    }

    public ShopService shops() {
        return shops;
    }

    /** Ankündigungen seit dem letzten Aufruf, z. B. Aufstiege und Abstimmungen; danach ist die Liste leer. */
    public List<Announcement> takeAnnouncements() {
        return announcements.take();
    }

    /** Baut übersetzbare Texte, z. B. Geldbeträge und Namen. */
    public Texts texts() {
        return texts;
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
