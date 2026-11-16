package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.constitution.ChangePreamble;
import de.etiennebader.civ.application.constitution.ProclaimConstitution;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawCash;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.federation.JoinFederation;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.land.OfferPlot;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.law.EnactParagraph;
import de.etiennebader.civ.application.market.BuyFromMarket;
import de.etiennebader.civ.application.market.SellToMarket;
import de.etiennebader.civ.application.office.AppointOfficer;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.CastBallot;
import de.etiennebader.civ.application.office.MakePromise;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.persistence.ChangeTracker;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.application.persistence.PendingSave;
import de.etiennebader.civ.application.persistence.StateDump;
import de.etiennebader.civ.application.persistence.WorldPersistence;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.ProposeLawChange;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.polity.RepealStatute;
import de.etiennebader.civ.application.shop.OpenShop;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.application.territory.ReleaseChunk;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.PersistenceException;
import de.etiennebader.civ.persistence.migration.Migration;
import de.etiennebader.civ.persistence.migration.SchemaMigrator;
import de.etiennebader.civ.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteStateStoreTest {

    private static final CurrencyId EMERALD = CurrencyId.EMERALD;
    private static final DimensionId OVERWORLD = DimensionId.OVERWORLD;

    private final Path file = tempFile();

    @Test
    @DisplayName("Ein vollständiger Weltzustand übersteht Speichern und Laden unverändert")
    void roundTripsCompleteWorld() {
        String saved;
        try (Session session = Session.open(file)) {
            session.buildWorld();
            assertTrue(session.persistence.flush().events() > 0);
            saved = session.dump();
        }
        assertTrue(saved.contains("  office|TREASURER|"), saved);
        assertTrue(saved.contains("  election|"), saved);
        assertTrue(saved.contains(":LOW_TAXES"), saved);
        assertTrue(saved.contains("  parliament|"), saved);
        assertTrue(saved.contains("  result|"), saved);
        assertTrue(saved.contains("  promise|"), saved);
        assertTrue(saved.contains("  poster|"), saved);
        assertTrue(saved.contains("street|Hauptstraße"), saved);
        assertTrue(saved.contains("contract|1|"), saved);
        assertTrue(saved.contains("contract|2|"), saved);
        assertTrue(saved.contains("letter|"), saved);
        assertTrue(saved.contains("friends|"), saved);
        assertTrue(saved.contains("request|"), saved);
        assertTrue(saved.contains("chat|2|"), saved);
        assertTrue(saved.contains("listing|"), saved);
        assertTrue(saved.contains("holding|"), saved);
        assertTrue(saved.contains("order|"), saved);
        assertTrue(saved.contains("trade|"), saved);
        assertTrue(saved.contains("quote|"), saved);
        assertTrue(saved.contains("dividend|"), saved);
        assertTrue(saved.contains("article|1|"), saved);

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            assertEquals(saved, reloaded.dump());
        }
    }

    @Test
    @DisplayName("Eine aufgelöste Gemeinde verschwindet samt Gebiet und Botschaft aus der Datenbank")
    void removesDissolvedPolity() {
        String saved;
        try (Session session = Session.open(file)) {
            session.buildWorld();
            session.persistence.flush();

            session.dissolveEichenfurt();
            session.persistence.flush();
            saved = session.dump();
        }

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            assertEquals(saved, reloaded.dump());
            assertTrue(saved.contains("constitution|d1|Wir, die Bürger von Nordhain."), saved);
            assertTrue(saved.contains("constitution|draft|Entwurf des Landes."), saved);
            assertTrue(saved.contains("|text=Feuer ist verboten. Wer stiehlt, wird verbannt."), saved);
            assertTrue(saved.contains("enact:Fristen:Abstimmungen dauern 4 Tage.:vote_days=4"), saved);
            assertTrue(saved.contains("repeal:"), saved);
            assertTrue(saved.contains("party|60|-10|minecraft:blue_banner"), saved);
            assertEquals(5, count("SELECT COUNT(*) FROM polity"),
                    "Nordhain, der Bund Westmark, die Ordnungspartei, die Holz AG und der Nordhainer Bote");
            assertEquals(1, count("SELECT COUNT(*) FROM polity WHERE level = 'land'"));
            assertEquals(0, count("SELECT COUNT(*) FROM plot WHERE type = 'EMBASSY'"));
            assertEquals(1, count("SELECT COUNT(*) FROM shop"));
        }
    }

    @Test
    @DisplayName("Spätere Änderungen werden inkrementell geschrieben, auch Löschungen")
    void writesIncrementalChanges() {
        String saved;
        try (Session session = Session.open(file)) {
            session.buildWorld();
            session.persistence.flush();

            session.changeWorld();
            FlushReport report = session.persistence.flush();

            assertEquals(1, report.polities());
            assertEquals(1, report.chunks());
            assertEquals(2, report.plots());
            saved = session.dump();
        }

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            assertEquals(saved, reloaded.dump());
            assertEquals(0, count("SELECT COUNT(*) FROM claim WHERE type = 'OUTPOST'"));
            assertEquals(0, count("SELECT COUNT(*) FROM election"));
            assertEquals(1, count("SELECT COUNT(*) FROM office WHERE office = 'PLANNER'"));
            assertEquals(0, count("SELECT COUNT(*) FROM office WHERE office = 'TREASURER'"));
        }
    }

    @Test
    @DisplayName("Jedes Event landet als JSON im Audit-Log")
    void appendsAuditLog() {
        try (Session session = Session.open(file)) {
            session.buildWorld();
            FlushReport report = session.persistence.flush();

            assertEquals(report.events(), count("SELECT COUNT(*) FROM domain_event"));
            assertEquals(2, count("SELECT COUNT(*) FROM domain_event WHERE type = 'PolityFounded' AND payload LIKE '{\"polity\":%'"));
        }
    }

    @Test
    @DisplayName("Ein inkonsistenter Speicherstand wird beim Laden erkannt")
    void detectsCorruptState() {
        try (Session session = Session.open(file)) {
            session.buildWorld();
            session.persistence.flush();
        }
        try (SqliteDatabase database = SqliteDatabase.open(file)) {
            database.inTransaction(connection -> {
                try (Statement statement = connection.createStatement()) {
                    return statement.executeUpdate("UPDATE currency_circulation SET supply = supply + 1");
                }
            });
        }

        try (Session reloaded = Session.open(file)) {
            assertThrows(PersistenceException.class, reloaded.persistence::restore);
        }
    }

    @Test
    @DisplayName("Kalender und Spielernamen werden mitgespeichert, Umbenennungen überschreiben den alten Namen")
    void roundTripsCalendarAndPlayerNames() {
        CalendarState savedCalendar;
        try (Session session = Session.open(file)) {
            session.players.record(session.alice, "Alice");
            session.players.record(session.carla, "Carla");
            session.calendar.completeDay();
            session.calendar.tick();
            session.persistence.flush();
            session.players.record(session.alice, "Alicia");
            session.calendar.tick();
            session.persistence.flush();
            savedCalendar = session.calendar.state();
        }

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            assertEquals(savedCalendar, reloaded.calendar.state());
            assertEquals(new CalendarState(Day.of(2), 2), savedCalendar);
            assertEquals(Optional.of("Alicia"), reloaded.players.nameOf(reloaded.alice));
            assertEquals(Optional.of("Carla"), reloaded.players.nameOf(reloaded.carla));
            assertFalse(reloaded.persistence.hasChanges());
        }
    }

    @Test
    @DisplayName("Ein Speicherstand lässt sich in einem anderen Thread schreiben")
    void writesPreparedSaveFromOtherThread() throws Exception {
        String saved;
        try (Session session = Session.open(file)) {
            session.buildWorld();
            PendingSave save = session.persistence.prepareSave();
            saved = session.dump();
            session.changeWorld();

            Thread writer = Thread.ofPlatform().start(() -> session.persistence.write(save));
            writer.join();
        }

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            assertEquals(saved, reloaded.dump());
        }
    }

    @Test
    @DisplayName("Gemeinden aus Etappe 1 ohne Gemeindehaus lassen sich nach der Migration laden")
    void loadsPolitiesWithoutSeatAfterMigration() {
        try (SqliteDatabase database = SqliteDatabase.open(file)) {
            new SchemaMigrator(List.of(Migration.fromResource("V001__initial_schema.sql"),
                    Migration.fromResource("V002__calendar_and_players.sql"))).migrate(database);
            database.inTransaction(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO polity (id, seq, name, kind, tier, currency, founded_on)
                            VALUES ('00000000-0000-0000-0000-000000000001', 1, 'Altdorf', 'TERRITORIAL', 'MUNICIPALITY',
                                    'EMERALD', 1)""");
                    return statement.executeUpdate("""
                            INSERT INTO membership (polity_id, position, actor_kind, actor_id, type, since)
                            VALUES ('00000000-0000-0000-0000-000000000001', 0, 'PLAYER',
                                    '00000000-0000-0000-0000-000000000002', 'CITIZEN', 1)""");
                }
            });
        }

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            PolityView altdorf = reloaded.app.queries().polities().getFirst();
            assertEquals("Altdorf", altdorf.name().value());
            assertTrue(altdorf.seat().isEmpty());
        }
    }

    /** Zählt über eine eigene Verbindung, wie ein externes Werkzeug. */
    private int count(String sql) {
        try (SqliteDatabase database = SqliteDatabase.open(file)) {
            return database.inTransaction(connection -> {
                try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                    result.next();
                    return result.getInt(1);
                }
            });
        }
    }

    private static Path tempFile() {
        try {
            return Files.createTempDirectory("civ-store").resolve("civ.sqlite");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Eine Application mit SQLite-Speicher, wie sie später im Spiel verdrahtet wird. */
    private static final class Session implements AutoCloseable {

        private static final Day DAY = Day.of(1);

        private final SeededIdGenerator ids = new SeededIdGenerator(17);
        private final ChangeTracker tracker = new ChangeTracker();
        private final Repositories repositories = InMemoryRepositories.create();
        private final CivApplication app = new CivApplication(repositories, ids, CompositeClaimPolicy.standard(4, 1), tracker);
        private final Calendar calendar = new Calendar(100, CalendarState.START);
        private final PlayerDirectory players = new PlayerDirectory();
        private final SqliteStateStore store;
        private final WorldPersistence persistence;

        private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
        private final PersonRef bertram = new PersonRef(PersonId.generate(ids));
        private final PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
        private final PlayerRef dora = new PlayerRef(PlayerId.generate(ids));
        private PolityId nordhain;
        private PolityId eichenfurt;
        private PlotId embassy;
        private PlotId home;
        private StatuteId outpostLaw;

        private Session(Path file) {
            this.store = SqliteStateStore.open(file);
            this.persistence = new WorldPersistence(repositories, store, tracker, calendar, players);
        }

        static Session open(Path file) {
            return new Session(file);
        }

        void buildWorld() {
            nordhain = app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, chunk(0, 0).center(64), DAY));
            eichenfurt = app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Eichenfurt"), carla, EMERALD, chunk(20, 20).center(64), DAY));
            app.joinPolity().execute(new JoinPolity.Command(nordhain, bertram, MembershipType.RESIDENT, DAY));
            app.joinPolity().execute(new JoinPolity.Command(nordhain, dora, MembershipType.CITIZEN, DAY));
            app.openShop().execute(new OpenShop.Command(new BlockPosition(DimensionId.OVERWORLD, 3, 64, 3), bertram,
                    Commodity.of("minecraft:bread"), money(15), DAY));
            app.proposeLawChange().execute(new ProposeLawChange.Command(nordhain, alice,
                    ParameterValue.of(StandardParameters.EXPLOSIONS_ALLOWED, true), DAY));
            app.claimChunk().execute(new ClaimChunk.Command(nordhain, chunk(1, 0), ClaimType.CORE, DAY));
            app.appointOfficer().execute(new AppointOfficer.Command(nordhain, alice, Office.TREASURER, Optional.of(dora),
                    DAY));
            ElectionId plannerElection = app.callElection().execute(new CallElection.Command(nordhain, dora, Office.PLANNER,
                    DAY));
            app.standForElection().execute(new StandForElection.Command(nordhain, plannerElection, dora, Platform.LOW_TAXES,
                    DAY));
            app.standForElection().execute(new StandForElection.Command(nordhain, plannerElection, alice, DAY));
            app.castBallot().execute(new CastBallot.Command(nordhain, plannerElection, alice, dora, DAY));
            outpostLaw = app.enactStatute().execute(new EnactStatute.Command(nordhain, "Außenpostengesetz",
                    List.of(ParameterValue.of(StandardParameters.MAX_OUTPOSTS, 1)), DAY));
            app.enactStatute().execute(new EnactStatute.Command(nordhain, "Grundsteuerordnung",
                    List.of(ParameterValue.of(StandardParameters.LAND_TAX_PER_BLOCK, 2L),
                            ParameterValue.of(StandardParameters.TAX_EMBASSIES, true)), DAY));
            app.claimChunk().execute(new ClaimChunk.Command(nordhain, chunk(9, 9), ClaimType.OUTPOST, DAY));

            home = app.createPlot().execute(new CreatePlot.Command(nordhain, new Area(OVERWORLD, 0, 0, 7, 7), PlotType.RESIDENTIAL, DAY));
            PlotId shop = app.createPlot().execute(new CreatePlot.Command(nordhain, new Area(OVERWORLD, 16, 0, 25, 5), PlotType.COMMERCIAL, DAY));
            embassy = app.createPlot().execute(new CreatePlot.Command(nordhain, new Area(OVERWORLD, 8, 0, 11, 3), PlotType.EMBASSY, DAY));
            app.assignPlot().execute(new AssignPlot.Command(nordhain, embassy, new PolityRef(eichenfurt), DAY));

            app.depositEmeralds().execute(new DepositEmeralds.Command(alice, money(5_000), DAY));
            app.depositEmeralds().execute(new DepositEmeralds.Command(bertram, money(800), DAY));
            app.sellPlot().execute(new SellPlot.Command(nordhain, home, new PolityRef(nordhain), alice, money(1_000), DAY));
            app.sellPlot().execute(new SellPlot.Command(nordhain, shop, new PolityRef(nordhain), bertram, money(300), DAY));
            app.offerPlot().execute(new OfferPlot.Command(nordhain, shop, bertram, Optional.of(money(450)), DAY));
            app.withdrawCash().execute(new WithdrawCash.Command(bertram, money(200), DAY));
            app.sellToMarket().execute(new SellToMarket.Command(alice, new Commodity("minecraft:diamond"), 5, 800,
                    PriceLevel.NEUTRAL, DAY));
            app.sellToMarket().execute(new SellToMarket.Command(bertram, new Commodity("minecraft:oak_log"), 64, 10,
                    PriceLevel.NEUTRAL, DAY));
            app.buyFromMarket().execute(new BuyFromMarket.Command(alice, new Commodity("minecraft:oak_log"), 4, 10,
                    PriceLevel.NEUTRAL, DAY));
            PolityId westmark = app.foundFederation().execute(new FoundFederation.Command(PolityName.of("Westmark"),
                    "land", nordhain, alice, DAY));
            app.joinFederation().execute(new JoinFederation.Command(westmark, eichenfurt, DAY));
            app.changePreamble().execute(new ChangePreamble.Command(nordhain, alice, "Wir, die Bürger von Nordhain.", DAY));
            app.proclaimConstitution().execute(new ProclaimConstitution.Command(nordhain, alice, DAY));
            app.changePreamble().execute(new ChangePreamble.Command(westmark, alice, "Entwurf des Landes.", DAY));
            var house = app.enactParagraph().execute(new EnactParagraph.Command(nordhain, alice, "Hausordnung",
                    "Feuer ist verboten. Wer stiehlt, wird verbannt.", DAY)).statute().orElseThrow();
            app.enactParagraph().execute(new EnactParagraph.Command(nordhain, alice, "Fristen",
                    "Abstimmungen dauern 4 Tage.", DAY));
            app.foundParty().execute(new FoundParty.Command(PolityName.of("Ordnungspartei"), dora,
                    new PartyProfile(60, -10, "minecraft:blue_banner"), EMERALD, List.of(bertram), DAY));
            app.proposeMotion().execute(new ProposeMotion.Command(nordhain, dora,
                    new Motion.Repeal(house, "Hausordnung"), DAY));
            app.amendLaw().execute(new AmendLaw.Command(eichenfurt, carla,
                    ParameterValue.of(StandardParameters.ELECTION_DAYS, 1L), DAY));
            app.amendLaw().execute(new AmendLaw.Command(eichenfurt, carla,
                    ParameterValue.of(StandardParameters.PARLIAMENT_SEATS, 3L), DAY));
            ElectionId parliament = app.callElection().execute(new CallElection.Command(eichenfurt, carla,
                    Office.PARLIAMENT, DAY));
            app.standForElection().execute(new StandForElection.Command(eichenfurt, parliament, carla, DAY));
            app.castBallot().execute(new CastBallot.Command(eichenfurt, parliament, carla, carla, DAY));
            app.makePromise().execute(new MakePromise.Command(nordhain, dora, "Die Umsatzsteuer beträgt 5 %.", DAY));
            app.putUpPoster().execute(chunk(0, 0).center(64), dora, DAY);
            app.streets().lay(alice, nordhain, "Hauptstraße", new Area(OVERWORLD, 0, 8, 15, 9), DAY);
            var lease = app.contracts().offerLease(alice, nordhain, home, dora, money(2), money(5), 10, DAY);
            app.depositEmeralds().execute(new DepositEmeralds.Command(dora, money(50), DAY));
            app.contracts().sign(lease.id(), dora, DAY);
            app.contracts().offerCustom(alice, carla, money(1), money(0), "Holzlieferung", "Jede Woche 64 Holz.", 7, DAY);
            app.community().send(alice, dora, "Willkommen", "Schön, dass du da bist.\nGruß", DAY);
            app.community().befriend(alice, dora, DAY);
            app.community().befriend(dora, alice, DAY);
            app.community().befriend(carla, alice, DAY);
            app.community().post(alice, Optional.empty(), "Hallo Welt", 1000, "game", DAY);
            app.community().post(dora, Optional.of(alice), "Danke!", 2000, "web", DAY);
            var company = app.companies().foundCompany(alice, PolityName.of("Holz AG"), money(1_000), DAY);
            app.trading().place(alice, company, de.etiennebader.civ.domain.exchange.Side.SELL, 100, 2, DAY);
            app.trading().place(dora, company, de.etiennebader.civ.domain.exchange.Side.BUY, 10, 2, DAY);
            app.trading().place(alice, company, de.etiennebader.civ.domain.exchange.Side.SELL, 50, 5, DAY);
            app.companies().payDividend(alice, company, money(1_000), DAY);
            var paper = app.companies().found(alice, PolityName.of("Nordhainer Bote"),
                    de.etiennebader.civ.domain.polity.PolityKind.NEWSPAPER, EMERALD, DAY);
            app.companies().admit(alice, paper, de.etiennebader.civ.domain.polity.PolityKind.NEWSPAPER, bertram, DAY);
            app.press().publish(alice, paper, "Holz AG zahlt Dividende", "Ein Erfolg.",
                    app.press().analyse("Holz AG zahlt Dividende", "Ein Erfolg.", java.util.Map.of()), DAY);
            app.civic().define(alice, nordhain, "Nordviertel", DAY);
            app.civic().addArea(alice, nordhain, "Nordviertel", new de.etiennebader.civ.domain.civic.Area(
                    de.etiennebader.civ.domain.territory.DimensionId.OVERWORLD, 0, 0, 15, 15), DAY);
            app.civic().addTown(alice, nordhain, "Nordviertel", nordhain, DAY);
            app.civic().setFlag(alice, nordhain, Optional.of(new de.etiennebader.civ.domain.civic.Flag("red",
                    java.util.List.of(new de.etiennebader.civ.domain.civic.Flag.Layer("minecraft:stripe_top", "white")))),
                    DAY);
            app.civic().setAnthem(alice, nordhain, Optional.of(new de.etiennebader.civ.domain.civic.Anthem(
                    "Heimatlied", "bell", "C4 E4 G4 C5*2 - G4/2")), DAY);
            app.justice().appoint(alice, nordhain, de.etiennebader.civ.domain.justice.Role.JUDGE, dora, true, DAY);
            var lawsuit = app.justice().file(alice, nordhain, de.etiennebader.civ.domain.justice.CaseKind.CIVIL, bertram,
                    "Zaun beschädigt", "250", DAY);
            app.justice().open(dora, lawsuit.id(), DAY);
            app.justice().submit(alice, lawsuit.id(), "buch", "Foto", "Der Zaun ist kaputt.", 2, DAY);
            app.justice().testify(bertram, lawsuit.id(), "Das war ich nicht.", DAY);
            app.endOfDay().execute(DAY);
            app.runElections().execute(DAY.plus(1));
        }

        void changeWorld() {
            Day later = Day.of(5);
            app.appointOfficer().execute(new AppointOfficer.Command(nordhain, alice, Office.TREASURER, Optional.empty(),
                    later));
            app.runElections().execute(later);
            app.dissolvePlot().execute(new DissolvePlot.Command(nordhain, embassy, later));
            app.repealStatute().execute(new RepealStatute.Command(nordhain, outpostLaw, later));
            app.releaseChunk().execute(new ReleaseChunk.Command(nordhain, chunk(9, 9), later));
            app.sellPlot().execute(new SellPlot.Command(nordhain, home, alice, bertram, money(0), later));
            long all = app.queries().ledger().balanceOf(bertram, EMERALD).amount();
            app.transferFunds().execute(new TransferFunds.Command(bertram, alice, money(all), TransferReason.TRADE, later));
        }

        void dissolveEichenfurt() {
            app.dissolvePolity().execute(new DissolvePolity.Command(eichenfurt, carla, Day.of(6)));
        }

        String dump() {
            String dump = StateDump.of(app.queries());
            assertNotEquals("\n", dump);
            return dump;
        }

        private static ChunkCoord chunk(int x, int z) {
            return new ChunkCoord(OVERWORLD, x, z);
        }

        private static Money money(long amount) {
            return Money.of(amount, EMERALD);
        }

        @Override
        public void close() {
            store.close();
        }
    }
}
