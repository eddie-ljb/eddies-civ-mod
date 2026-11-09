package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.CommodityStock;
import de.etiennebader.civ.domain.market.Market;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lagerbestände des Servermarkts in der Tabelle {@code market_stock}.
 */
final class MarketTable {

    private static final String UPSERT = """
            INSERT INTO market_stock (commodity, currency, quantity, as_of) VALUES (?, ?, ?, ?)
            ON CONFLICT (commodity) DO UPDATE SET currency = excluded.currency, quantity = excluded.quantity,
                as_of = excluded.as_of""";

    void write(Connection connection, CurrencyId currency, Iterable<CommodityStock> stocks) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
            for (CommodityStock stock : stocks) {
                upsert.setString(1, stock.commodity().id());
                upsert.setString(2, currency.code());
                upsert.setLong(3, stock.quantity());
                upsert.setLong(4, stock.asOf().value());
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
    }

    Market load(Connection connection, CurrencyId currency) throws SQLException {
        List<CommodityStock> stocks = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT commodity, quantity, as_of FROM market_stock WHERE currency = ? ORDER BY commodity")) {
            select.setString(1, currency.code());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    stocks.add(new CommodityStock(Commodity.of(rows.getString("commodity")), rows.getLong("quantity"),
                            Day.of(rows.getLong("as_of"))));
                }
            }
        }
        return Market.restore(currency, stocks);
    }
}
