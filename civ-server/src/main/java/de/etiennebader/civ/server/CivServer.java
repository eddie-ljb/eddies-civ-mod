package de.etiennebader.civ.server;

import de.etiennebader.civ.server.election.DebateService;
import de.etiennebader.civ.server.election.LiveCountService;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.server.civic.CivicService;
import de.etiennebader.civ.server.civic.HomePortService;
import de.etiennebader.civ.server.justice.JusticeService;
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
import de.etiennebader.civ.server.credit.CreditService;
import de.etiennebader.civ.server.currency.CurrencyService;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.civil.CivilService;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.community.CommunityService;
import de.etiennebader.civ.server.company.CompanyService;
import de.etiennebader.civ.server.constitution.ConstitutionService;
import de.etiennebader.civ.server.federation.FederationService;
import de.etiennebader.civ.server.federation.VillagerFederations;
import de.etiennebader.civ.server.law.LawBookService;
import de.etiennebader.civ.server.level.LevelService;
import de.etiennebader.civ.server.market.ItemTraits;
import de.etiennebader.civ.server.market.MarketService;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.parliament.ParliamentService;
import de.etiennebader.civ.server.party.PartyService;
import de.etiennebader.civ.server.plot.PlotService;
import de.etiennebader.civ.server.press.PressService;
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
import java.util.concurrent.Executor;
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
    private final CurrencyService currencyService;
    private final CreditService creditService;
    private final MarketService market;
    private final TerritoryWatcher territory;
    private final TownService towns;
    private final PlotService plots;
    private final de.etiennebader.civ.server.construction.ConstructionService construction;
    private final de.etiennebader.civ.server.war.WarService war;
    private final de.etiennebader.civ.server.police.PoliceService police;
    private final de.etiennebader.civ.server.family.FamilyService family;
    private final de.etiennebader.civ.server.machine.MachineService machines;
    private final de.etiennebader.civ.server.religion.ReligionService religion;
    private final VillagerService villagers;
    private final de.etiennebader.civ.server.villager.EducationStore educationStore;
    private final VillagerFederations villagerFederations;
    private final de.etiennebader.civ.server.villager.VillagerFoundations villagerFoundations;
    private final de.etiennebader.civ.server.conjuncture.ConjunctureService conjuncture;
    private final Announcements announcements = new Announcements();
    private final VotingService voting;
    private final OfficeService offices;
    private final FederationService federations;
    private final LevelService levels;
    private final CivicService civic;
    private final HomePortService homePorts;
    private final JusticeService justice;
    private volatile java.nio.file.Path mapDirectory;
    private final Optional<java.nio.file.Path> imagesDirectory;
    private final LiveCountService liveCount;
    private final DebateService debates;
    private final Clock clock = Clock.systemUTC();
    private final ConstitutionService constitutions;
    private final LawBookService lawBooks;
    private final PartyService parties;
    private final ParliamentService parliaments;
    private final CampaignService campaigns;
    private final CivilService civil;
    private final CommunityService community;
    private final CompanyService companies;
    private final de.etiennebader.civ.server.ranking.RankingService rankings;
    private final PressService press;
    private final TownHallService townHalls;
    private final ShopService shops;
    private final ServerSettings settings;
    private final GameThread gameThread = new GameThread(WEB_TIMEOUT_MILLIS);
    private final WebSessions webSessions = new WebSessions(Clock.systemUTC());
    private MiniHttpServer web;
    private final Texts texts;
    private boolean running = true;

    private CivServer(StateStore store, AutoCloseable storage, ServerSettings settings, IdGenerator ids,
                      SaveListener listener, Optional<java.nio.file.Path> imagesDirectory,
                      Optional<java.nio.file.Path> dataDirectory) {
        Repositories repositories = InMemoryRepositories.create();
        ChangeTracker tracker = new ChangeTracker();
        this.storage = storage;
        this.settings = settings;
        this.imagesDirectory = imagesDirectory;
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
        this.offices = new OfficeService(app, calendar, players, texts, announcements);
        this.federations = new FederationService(app, calendar, settings.levels(), texts, announcements,
                settings.founding(), settings.currency());
        this.commands = new CivCommands(app, calendar, settings.currency(), texts, protection, voting,
                settings.founding());
        this.parties = new PartyService(app, calendar, settings.currency(), settings.parties(), players, texts,
                announcements);
        this.lawBooks = new LawBookService(app, calendar, texts, announcements, federations,
                settings.persistLawDrafts() ? dataDirectory.map(dir -> dir.resolve("law-drafts.tsv")) : Optional.empty());
        this.parliaments = new ParliamentService(app, texts, offices, voting);
        this.campaigns = new CampaignService(app, calendar, texts, offices);
        this.constitutions = new ConstitutionService(app, calendar, texts, announcements, commands, offices, voting,
                federations);
        this.bank = new BankService(app, calendar, settings.currency(), players, texts);
        this.currencyService = new CurrencyService(app, calendar, players, texts);
        this.creditService = new CreditService(app, calendar, players, texts);
        this.market = new MarketService(app, calendar, settings, players, texts);
        this.territory = new TerritoryWatcher(app.queries(), texts);
        this.towns = new TownService(app.queries(), settings, texts, commands, territory);
        this.plots = new PlotService(app, calendar, players, texts);
        this.construction = new de.etiennebader.civ.server.construction.ConstructionService(app, calendar, texts,
                settings.ticksPerDay(), new de.etiennebader.civ.server.construction.BlueprintLibrary(
                dataDirectory.map(dir -> dir.resolve("blueprints"))), dataDirectory.map(dir -> dir.resolve("construction.tsv")), announcements);
        this.war = new de.etiennebader.civ.server.war.WarService(app, calendar, texts, announcements, ids,
                settings, dataDirectory.map(dir -> dir.resolve("war.tsv")), players);
        this.police = new de.etiennebader.civ.server.police.PoliceService(app, calendar, players, texts,
                announcements, settings, dataDirectory.map(dir -> dir.resolve("police.tsv")));
        this.villagerFederations = new VillagerFederations(app, federations, announcements);
        this.conjuncture = new de.etiennebader.civ.server.conjuncture.ConjunctureService(app.queries());
        this.villagers = new VillagerService(app, settings, texts, announcements, conjuncture, calendar);
        this.educationStore = new de.etiennebader.civ.server.villager.EducationStore(
                dataDirectory.map(dir -> dir.resolve("education.tsv")));
        educationStore.load(app.villagerEducation());
        this.family = new de.etiennebader.civ.server.family.FamilyService(app.queries(), announcements,
                dataDirectory.map(dir -> dir.resolve("family.tsv")));
        this.religion = new de.etiennebader.civ.server.religion.ReligionService(app.queries(), players, announcements,
                ids, dataDirectory.map(dir -> dir.resolve("religion.tsv")));
        villagers.faithPoints(religion::faithPoints);
        villagers.familyLines(villager -> {
            List<de.etiennebader.civ.server.command.Message> lines = new java.util.ArrayList<>(family.describe(villager));
            lines.addAll(religion.describe(villager));
            return lines;
        });
        this.villagerFoundations = new de.etiennebader.civ.server.villager.VillagerFoundations(app, villagers::lastSeen,
                announcements, settings.founding());
        this.townHalls = new TownHallService(app, calendar, texts, announcements);
        this.shops = new ShopService(app, calendar, settings.currency(), texts);
        this.civil = new CivilService(app, calendar, players, settings.currency(), texts, announcements,
                player -> plots.selection(player).area());
        this.community = new CommunityService(app, calendar, players, texts, announcements, Clock.systemUTC());
        this.companies = new CompanyService(app, calendar, players, settings.currency(), texts, announcements,
                conjuncture, settings.founding());
        this.machines = new de.etiennebader.civ.server.machine.MachineService(app, settings, texts, announcements, ids,
                calendar::today, dataDirectory.map(dir -> dir.resolve("machines.tsv")));
        app.production().rules(machines);
        app.companies().foundingFee(settings.founding().company());
        this.companies.pricing(market::priceLevel);
        this.rankings = new de.etiennebader.civ.server.ranking.RankingService(app, texts, settings.currency(),
                companies, federations);
        this.press = new PressService(app, calendar, players, settings.currency(), texts, announcements);
        this.civic = new CivicService(app, calendar, texts, announcements, plots);
        this.homePorts = new HomePortService(app, calendar, texts, war::registry, settings.warPortLock());
        this.justice = new JusticeService(app, calendar, players, texts, announcements,
                dataDirectory.map(dir -> dir.resolve("court_candidacies.tsv")));
        this.justice.addListener(police);
        this.war.onNews(this::warNews);
        this.liveCount = new LiveCountService(announcements, settings.show().liveCount(),
                settings.show().liveCountSeconds(), settings.show().projections());
        this.debates = new DebateService(app, calendar, texts, announcements, settings.show().debateMinutes());
        offices.liveCount(liveCount, clock);
        this.levels = new LevelService(app.queries(), texts, federations, commands, offices, voting, parliaments,
                constitutions, campaigns, lawBooks, civic, debates);
        app.runElections().residence(this::residenceOf);
        app.pressInfluence().today(calendar.today());
    }

    /**
     * Öffnet die Datenbank (legt sie bei Bedarf an), bringt das Schema auf den neuesten Stand und lädt den Zustand.
     *
     * @throws de.etiennebader.civ.persistence.PersistenceException wenn die Datei nicht geöffnet oder gelesen werden kann
     */
    public static CivServer open(Path databaseFile, ServerSettings settings, SaveListener listener) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        SqliteStateStore store = SqliteStateStore.open(databaseFile);
        Path parent = databaseFile.toAbsolutePath().getParent();
        Optional<Path> images = Optional.ofNullable(parent).map(dir -> dir.resolve("images"));
        try {
            return new CivServer(store, store, settings, new RandomIdGenerator(), listener, images,
                    Optional.ofNullable(parent));
        } catch (RuntimeException e) {
            store.close();
            throw e;
        }
    }

    /** Für Tests: mit beliebigem Speicher und vorhersagbaren IDs. */
    static CivServer start(StateStore store, ServerSettings settings, IdGenerator ids, SaveListener listener) {
        return new CivServer(store, () -> {
        }, settings, ids, listener, Optional.empty(), Optional.empty());
    }

    /**
     * Einmal pro Spieltick aufrufen. Endet dabei ein Tag, wird er abgeschlossen (Grundsteuer usw.).
     *
     * @return der Bericht des gerade beendeten Tages, falls einer endete
     */
    public Optional<DayReport> tick() {
        requireRunning();
        gameThread.runPending();
        long now = clock.millis();
        liveCount.tick(now);
        debates.tick(now);
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
        offices.endOfDay(day);
        parties.endOfDay(day);
        villagers.endOfDay(day);
        educationStore.save(app.villagerEducation());
        app.credit().endOfDay(day); // überfällige Kredite fallen aus
        if (settings.ambitions()) {
            villagerFoundations.endOfDay(day);
            villagerFederations.endOfDay(day);
            construction.villagersOrder(day);
            family.endOfDay(day);
        }
        religion.endOfDay(day);
        federations.autoDecideInvitations(day, settings.villagerPolitics());
        campaigns.endOfDay(day);
        war.endOfDay(day);
        police.endOfDay(day);
        civil.endOfDay(day);
        justice.endOfDay(day);
        companies.endOfDay(day);
        machines.endOfDay(day, settings.ambitions());
        companies.produce(day);
        press.endOfDay(day.plus(1));
        community.endOfDay(day.plus(1));
        debates.newDay(day.plus(1), clock.millis());
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
        community.left(player);
    }

    /** Merkt sich den Namen eines Spielers, der den Server betritt. */
    public void playerJoined(PlayerRef player, String name) {
        requireRunning();
        players.record(player, name);
        community.joined(player);
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
     * @param mainThread führt Aufgaben auf dem Spielthread aus, auch wenn ein Einzelspieler-Spiel pausiert ist
     * @return der tatsächliche Port; leer, wenn die Webseite ausgeschaltet ist
     * @throws IOException wenn der Port belegt ist
     */
    public Optional<Integer> startWeb(Function<String, Optional<ItemTraits>> items, Executor mainThread)
            throws IOException {
        requireRunning();
        gameThread.wakeVia(mainThread);
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

    /** {@code /civ web abmelden}: beendet alle Sitzungen des Spielers auf der Webseite. */
    public Reply webLogout(Caller caller) {
        requireRunning();
        return Reply.success(Message.of(MessageKey.WEB_LOGGED_OUT, webSessions.logoutAll(caller.player())));
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

    public CurrencyService currencies() {
        return currencyService;
    }

    public CreditService credit() {
        return creditService;
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

    /** Kriegsberichte erscheinen in jeder Zeitung, die einen Dorfbewohner als Redakteur hat. */
    private void warNews(String title, String body) {
        for (de.etiennebader.civ.domain.polity.PolityView paper : app.queries().newspapers()) {
            paper.members().stream().map(de.etiennebader.civ.domain.polity.Membership::member)
                    .filter(de.etiennebader.civ.domain.actor.PersonRef.class::isInstance)
                    .sorted(java.util.Comparator.comparing(de.etiennebader.civ.domain.actor.ActorRef::uuid)).findFirst()
                    .ifPresent(editor -> press.publish(editor, paper.name().toString(), title, body));
        }
    }

    public de.etiennebader.civ.server.police.PoliceService police() {
        return police;
    }

    public de.etiennebader.civ.server.war.WarService war() {
        return war;
    }

    /** Religionen, Klerus, Gläubigkeit der Dorfbewohner (Issue #13). */
    public de.etiennebader.civ.server.religion.ReligionService religion() {
        return religion;
    }

    /** Maschinen, Wartung und Zusatzwaren der Firmen. */
    public de.etiennebader.civ.server.machine.MachineService machines() {
        return machines;
    }

    /** Heirat, Scheidung und Kinder der Dorfbewohner. */
    public de.etiennebader.civ.server.family.FamilyService family() {
        return family;
    }

    public de.etiennebader.civ.server.construction.ConstructionService construction() {
        return construction;
    }

    public PlotService plots() {
        return plots;
    }

    public VillagerService villagers() {
        return villagers;
    }

    public de.etiennebader.civ.server.conjuncture.ConjunctureService conjuncture() {
        return conjuncture;
    }

    public OfficeService offices() {
        return offices;
    }

    public PartyService parties() {
        return parties;
    }

    public CivilService civil() {
        return civil;
    }

    public CommunityService community() {
        return community;
    }

    public de.etiennebader.civ.server.ranking.RankingService rankings() {
        return rankings;
    }

    public CompanyService companies() {
        return companies;
    }

    public PressService press() {
        return press;
    }

    public CampaignService campaigns() {
        return campaigns;
    }

    public ParliamentService parliaments() {
        return parliaments;
    }

    public LawBookService lawBooks() {
        return lawBooks;
    }

    public ConstitutionService constitutions() {
        return constitutions;
    }

    public FederationService federations() {
        return federations;
    }

    /** Live-Auszählung von Wahlergebnissen. */
    public LiveCountService liveCount() {
        return liveCount;
    }

    /** Wahldebatten. */
    public DebateService debates() {
        return debates;
    }

    /** Wahlkreise und Hoheitszeichen. */
    public HomePortService homePorts() {
        return homePorts;
    }

    public CivicService civic() {
        return civic;
    }

    /** Ordner mit den gerenderten Kartenkacheln ({@code <dim>/<x>_<z>.png}); leer ohne Weltkarte. */
    public Optional<java.nio.file.Path> mapDirectory() {
        return settings.show().worldMap() ? Optional.ofNullable(mapDirectory) : Optional.empty();
    }

    /** Setzt den Ordner der Kartenkacheln; die Fabric-Seite rendert hinein. */
    public void mapDirectory(java.nio.file.Path directory) {
        this.mapDirectory = directory;
    }

    /**
     * Ordner für selbst hochgeladene Bilder (Wappen, Wahlplakate, Leuchtreklamen), benannt nach ihrem Hash
     * ({@code <hash>.png}); leer in Tests ohne echte Datenbankdatei.
     */
    public Optional<java.nio.file.Path> imagesDirectory() {
        return imagesDirectory;
    }

    /**
     * Schreibt sofort eine Sicherung der Datenbank ({@code backups/civ-manual-…sqlite}).
     *
     * @return die Datei; leer, wenn ohne SQLite (Tests)
     */
    public Optional<Path> backupNow() {
        save();
        if (storage instanceof SqliteStateStore sqlite) {
            return Optional.of(sqlite.backupNow());
        }
        return Optional.empty();
    }

    /** Gerichte und Verfahren. */
    public JusticeService justice() {
        return justice;
    }

    /**
     * Wohnsitz für Wahlkreise: bei Spielern die Mitte ihres Wohngrundstücks, bei Dorfbewohnern, wo sie zuletzt gesehen
     * wurden.
     */
    private Optional<BlockPosition> residenceOf(ActorRef actor) {
        if (actor instanceof PersonRef villager) {
            return villagers.lastSeen(villager);
        }
        return app.addresses().residenceOf(actor).flatMap(address -> app.queries().land(address.town())
                .plot(address.plot()).map(plot -> new BlockPosition(plot.area().dimension(),
                        (plot.area().minX() + plot.area().maxX()) / 2, 64, (plot.area().minZ() + plot.area().maxZ()) / 2)));
    }

    /** Befehle je politischer Ebene. */
    public LevelService levels() {
        return levels;
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
    /** Die Einstellungen dieses Servers. */
    public ServerSettings settings() {
        return settings;
    }

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

    public de.etiennebader.civ.server.villager.VillagerFoundations villagerFoundations() {
        return villagerFoundations;
    }

    public VillagerFederations villagerFederations() {
        return villagerFederations;
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
