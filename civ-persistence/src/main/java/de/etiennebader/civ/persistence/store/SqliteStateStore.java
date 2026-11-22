package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.StateDelta.PolityChange;
import de.etiennebader.civ.application.persistence.StateDelta;
import de.etiennebader.civ.application.persistence.StateStore;
import de.etiennebader.civ.application.persistence.StoredWorld;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotSnapshot;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.persistence.migration.SchemaMigrator;
import de.etiennebader.civ.persistence.PersistenceException;
import de.etiennebader.civ.persistence.sqlite.Backups;
import de.etiennebader.civ.persistence.sqlite.SqliteDatabase;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link StateStore} auf Basis einer SQLite-Datei.
 *
 * <p>Laden liest alle Tabellen in einer Transaktion und baut die Aggregate über ihre
 * {@code restore}-Fabriken wieder auf; dabei werden die Invarianten erneut geprüft. Schreiben
 * übernimmt genau die Einträge aus dem {@link StateDelta} in einer Transaktion und hängt die Events
 * an das Audit-Log an. Schreiben darf in einem anderen Thread laufen als Laden, aber nie in mehreren zugleich.
 */
public final class SqliteStateStore implements StateStore, AutoCloseable {

    /** Es gibt vorerst einen Servermarkt in Smaragden. */
    private static final CurrencyId MARKET_CURRENCY = CurrencyId.EMERALD;

    private final SqliteDatabase database;
    private long lastBackupDay = -1;
    private final PolityTable polityTable;
    private final TerritoryTable territoryTable = new TerritoryTable();
    private final PlotTable plotTable = new PlotTable();
    private final LedgerTable ledgerTable = new LedgerTable();
    private final EventTable eventTable = new EventTable();
    private final PlayerTable playerTable = new PlayerTable();
    private final CalendarTable calendarTable = new CalendarTable();
    private final MarketTable marketTable = new MarketTable();
    private final ShopTable shopTable = new ShopTable();
    private final CivilTables civilTables = new CivilTables();
    private final MarketPressTables marketPressTables = new MarketPressTables();
    private final CivicJusticeTables civicJusticeTables = new CivicJusticeTables();
    private final ProductionTables productionTables = new ProductionTables();
    private final CurrencyCreditTables currencyCreditTables = new CurrencyCreditTables();

    public SqliteStateStore(SqliteDatabase database, ParameterCatalog catalog) {
        this.database = Objects.requireNonNull(database, "database");
        this.polityTable = new PolityTable(catalog);
    }

    /**
     * Öffnet die Datei, bringt das Schema auf den neuesten Stand und nutzt die Standardparameter.
     *
     * @throws de.etiennebader.civ.persistence.migration.UnsupportedSchemaVersionException wenn die Datei neuer ist als der Mod
     */
    public static SqliteStateStore open(Path file) {
        SqliteDatabase database = SqliteDatabase.open(file);
        try {
            List<String> problems = database.outsideTransaction(Backups::problems);
            if (!problems.isEmpty()) {
                throw new PersistenceException("Die Datenbank " + database.file() + " ist beschädigt (" + problems.getFirst()
                        + "). Stelle eine Sicherung aus " + Backups.folder(database.file())
                        + " wieder her: Server stoppen, die Sicherung über civ.sqlite kopieren, neu starten.");
            }
            SchemaMigrator.standard().migrate(database);
            return new SqliteStateStore(database, ParameterCatalog.standard());
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }

    @Override
    public StoredWorld load() {
        return database.inTransaction(this::load);
    }

    private StoredWorld load(Connection connection) throws SQLException {
        try {
            List<Polity> polities = polityTable.loadAll(connection);
            Map<String, List<PlotSnapshot>> plots = plotTable.loadAll(connection);
            List<LandRegistry> land = new ArrayList<>();
            for (Polity polity : polities) {
                land.add(LandRegistry.restore(polity.id(), plots.getOrDefault(polity.id().value().toString(), List.of())));
            }
            TerritoryRegistry territory = TerritoryRegistry.restore(territoryTable.loadAll(connection));
            Ledger ledger = Ledger.restore(ledgerTable.loadBalances(connection), ledgerTable.loadTotals(connection));
            return new StoredWorld(polities, territory, land, ledger,
                    marketTable.load(connection, MARKET_CURRENCY), shopTable.load(connection), playerTable.loadAll(connection),
                    calendarTable.load(connection), civilTables.loadContracts(connection),
                    civilTables.loadCommunity(connection), marketPressTables.loadExchange(connection),
                    marketPressTables.loadPress(connection), civicJusticeTables.loadCivic(connection),
                    civicJusticeTables.loadJustice(connection), productionTables.load(connection),
                    currencyCreditTables.loadCurrencies(connection), currencyCreditTables.loadCredit(connection));
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("Gespeicherter Zustand ist ungültig: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(StateDelta delta) {
        Objects.requireNonNull(delta, "delta");
        database.inTransaction(connection -> {
            for (PolityChange change : delta.polities()) {
                if (change.polity().isPresent()) {
                    polityTable.write(connection, change.polity().get());
                }
            }
            territoryTable.write(connection, delta.claims());
            plotTable.write(connection, delta.plots());
            // Aufgelöste Polities erst nach ihren Claims und Grundstücken löschen (Fremdschlüssel).
            for (PolityChange change : delta.polities()) {
                if (change.polity().isEmpty()) {
                    polityTable.delete(connection, change.id());
                }
            }
            ledgerTable.writeBalances(connection, delta.balances());
            ledgerTable.writeTotals(connection, delta.currencies());
            marketTable.write(connection, MARKET_CURRENCY, delta.commodities());
            shopTable.write(connection, delta.shops());
            if (delta.contracts().isPresent()) {
                civilTables.writeContracts(connection, delta.contracts().get());
            }
            if (delta.community().isPresent()) {
                civilTables.writeCommunity(connection, delta.community().get());
            }
            if (delta.exchange().isPresent()) {
                marketPressTables.writeExchange(connection, delta.exchange().get());
            }
            if (delta.press().isPresent()) {
                marketPressTables.writePress(connection, delta.press().get());
            }
            if (delta.civic().isPresent()) {
                civicJusticeTables.writeCivic(connection, delta.civic().get());
            }
            if (delta.production().isPresent()) {
                productionTables.write(connection, delta.production().get());
            }
            if (delta.currencyRegistry().isPresent()) {
                currencyCreditTables.writeCurrencies(connection, delta.currencyRegistry().get());
            }
            if (delta.credit().isPresent()) {
                currencyCreditTables.writeCredit(connection, delta.credit().get());
            }
            if (delta.justice().isPresent()) {
                civicJusticeTables.writeJustice(connection, delta.justice().get());
            }
            eventTable.append(connection, delta.events());
            playerTable.write(connection, delta.players());
            calendarTable.write(connection, delta.calendar());
            return null;
        });
        dailyBackup(delta.calendar().today().value());
    }

    /** Einmal je Spieltag eine Sicherung; die letzten sieben bleiben. */
    private void dailyBackup(long day) {
        if (day == lastBackupDay) {
            return;
        }
        if (lastBackupDay >= 0) {
            try {
                database.outsideTransaction(connection -> Backups.write(connection, database.file(), "daily"));
            } catch (PersistenceException e) {
                // Eine fehlgeschlagene Sicherung darf das Speichern nicht scheitern lassen.
                System.getLogger(SqliteStateStore.class.getName()).log(System.Logger.Level.WARNING,
                        "Tägliche Sicherung fehlgeschlagen", e);
            }
        }
        lastBackupDay = day;
    }

    /** Schreibt sofort eine Sicherung, z. B. für {@code /civ admin sicherung}. */
    public Path backupNow() {
        return database.outsideTransaction(connection -> Backups.write(connection, database.file(), "manual"));
    }

    @Override
    public void close() {
        database.close();
    }
}
