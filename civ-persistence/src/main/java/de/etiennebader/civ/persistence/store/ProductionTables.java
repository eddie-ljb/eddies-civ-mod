package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.production.ProductionRegistry;
import de.etiennebader.civ.domain.production.ProductionView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tabellen der Firmenbetriebe; das Register wird als Ganzes geschrieben. */
final class ProductionTables {

    void write(Connection connection, ProductionView production) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM enterprise");
            clear.executeUpdate("DELETE FROM enterprise_stock");
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO enterprise (company_id, branch, invested, auto_sell, last_day, last_output, last_revenue)
                VALUES (?, ?, ?, ?, ?, ?, ?)""");
             PreparedStatement stock = connection.prepareStatement(
                     "INSERT INTO enterprise_stock (company_id, commodity, quantity) VALUES (?, ?, ?)")) {
            for (Enterprise enterprise : production.enterprises()) {
                String id = enterprise.company().value().toString();
                insert.setString(1, id);
                insert.setString(2, enterprise.branch().name());
                insert.setLong(3, enterprise.invested());
                insert.setInt(4, enterprise.autoSell() ? 1 : 0);
                if (enterprise.lastDay().isPresent()) {
                    insert.setLong(5, enterprise.lastDay().get().value());
                } else {
                    insert.setNull(5, java.sql.Types.INTEGER);
                }
                insert.setLong(6, enterprise.lastOutput());
                insert.setLong(7, enterprise.lastRevenue());
                insert.addBatch();
                for (Map.Entry<Commodity, Long> entry : enterprise.stock().entrySet()) {
                    stock.setString(1, id);
                    stock.setString(2, entry.getKey().id());
                    stock.setLong(3, entry.getValue());
                    stock.addBatch();
                }
            }
            insert.executeBatch();
            stock.executeBatch();
        }
    }

    ProductionRegistry load(Connection connection) throws SQLException {
        Map<String, Map<Commodity, Long>> stocks = new HashMap<>();
        try (Statement select = connection.createStatement();
             ResultSet rows = select.executeQuery("SELECT company_id, commodity, quantity FROM enterprise_stock")) {
            while (rows.next()) {
                stocks.computeIfAbsent(rows.getString("company_id"), ignored -> new HashMap<>())
                        .put(Commodity.of(rows.getString("commodity")), rows.getLong("quantity"));
            }
        }
        List<Enterprise> enterprises = new ArrayList<>();
        try (Statement select = connection.createStatement();
             ResultSet rows = select.executeQuery("""
                     SELECT company_id, branch, invested, auto_sell, last_day, last_output, last_revenue
                     FROM enterprise ORDER BY company_id""")) {
            while (rows.next()) {
                String id = rows.getString("company_id");
                long lastDay = rows.getLong("last_day");
                Optional<Day> day = rows.wasNull() ? Optional.empty() : Optional.of(Day.of(lastDay));
                enterprises.add(new Enterprise(new PolityId(UUID.fromString(id)), Branch.valueOf(rows.getString("branch")),
                        rows.getLong("invested"), rows.getInt("auto_sell") == 1, stocks.getOrDefault(id, Map.of()), day,
                        rows.getLong("last_output"), rows.getLong("last_revenue")));
            }
        }
        return ProductionRegistry.restore(enterprises);
    }
}
