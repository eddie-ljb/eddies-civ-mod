package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawCash;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.persistence.ChangeTracker;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.application.persistence.PendingSave;
import de.etiennebader.civ.application.persistence.StateDump;
import de.etiennebader.civ.application.persistence.WorldPersistence;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.RepealStatute;
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
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.shared.StatuteId;
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

        try (Session reloaded = Session.open(file)) {
            reloaded.persistence.restore();

            assertEquals(saved, reloaded.dump());
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
            app.claimChunk().execute(new ClaimChunk.Command(nordhain, chunk(1, 0), ClaimType.CORE, DAY));
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
            app.withdrawCash().execute(new WithdrawCash.Command(bertram, money(200), DAY));
            app.endOfDay().execute(DAY);
        }

        void changeWorld() {
            Day later = Day.of(5);
            app.dissolvePlot().execute(new DissolvePlot.Command(nordhain, embassy, later));
            app.repealStatute().execute(new RepealStatute.Command(nordhain, outpostLaw, later));
            app.releaseChunk().execute(new ReleaseChunk.Command(nordhain, chunk(9, 9), later));
            app.sellPlot().execute(new SellPlot.Command(nordhain, home, alice, bertram, money(0), later));
            long all = app.queries().ledger().balanceOf(bertram, EMERALD).amount();
            app.transferFunds().execute(new TransferFunds.Command(bertram, alice, money(all), TransferReason.TRADE, later));
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
