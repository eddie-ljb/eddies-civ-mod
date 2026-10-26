package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.ChangeSet;
import de.etiennebader.civ.application.persistence.StateStore;
import de.etiennebader.civ.application.persistence.StoredWorld;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotSnapshot;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.persistence.PersistenceException;
import de.etiennebader.civ.persistence.migration.SchemaMigrator;
import de.etiennebader.civ.persistence.sqlite.SqliteDatabase;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link StateStore} auf Basis einer SQLite-Datei.
 *
 * <p>Laden liest alle Tabellen in einer Transaktion und baut die Aggregate über ihre
 * {@code restore}-Fabriken wieder auf; dabei werden die Invarianten erneut geprüft. Schreiben
 * übernimmt genau die Einträge aus dem {@link ChangeSet} in einer Transaktion und hängt die Events
 * an das Audit-Log an.
 */
public final class SqliteStateStore implements StateStore, AutoCloseable {

    private final SqliteDatabase database;
    private final PolityTable polityTable;
    private final TerritoryTable territoryTable = new TerritoryTable();
    private final PlotTable plotTable = new PlotTable();
    private final LedgerTable ledgerTable = new LedgerTable();
    private final EventTable eventTable = new EventTable();

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
            return new StoredWorld(polities, territory, land, ledger);
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("Gespeicherter Zustand ist ungültig: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(ChangeSet changes, WorldQueries current) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(current, "current");
        database.inTransaction(connection -> {
            for (PolityId polityId : changes.polities()) {
                Optional<PolityView> polity = current.polity(polityId);
                if (polity.isPresent()) {
                    polityTable.write(connection, polity.get());
                } else {
                    polityTable.delete(connection, polityId);
                }
            }
            territoryTable.write(connection, changes.chunks(), current.territory());
            plotTable.write(connection, changes.plots(), current);
            ledgerTable.writeAccounts(connection, changes.accounts(), current.ledger());
            ledgerTable.writeTotals(connection, changes.currencies(), current.ledger());
            eventTable.append(connection, changes.events());
            return null;
        });
    }

    @Override
    public void close() {
        database.close();
    }
}
