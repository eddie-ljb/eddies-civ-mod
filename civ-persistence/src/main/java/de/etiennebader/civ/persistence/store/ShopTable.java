package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.StateDelta.ShopChange;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shop.Shop;
import de.etiennebader.civ.domain.shop.ShopRegistry;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Läden in der Tabelle {@code shop}.
 */
final class ShopTable {

    private static final String UPSERT = """
            INSERT INTO shop (dimension, x, y, z, seq, owner_kind, owner_id, commodity, price, currency, opened_on)
            VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(seq), 0) + 1 FROM shop), ?, ?, ?, ?, ?, ?)
            ON CONFLICT (dimension, x, y, z) DO UPDATE SET owner_kind = excluded.owner_kind,
                owner_id = excluded.owner_id, commodity = excluded.commodity, price = excluded.price,
                currency = excluded.currency, opened_on = excluded.opened_on""";
    private static final String DELETE = "DELETE FROM shop WHERE dimension = ? AND x = ? AND y = ? AND z = ?";

    void write(Connection connection, List<ShopChange> changes) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT);
             PreparedStatement delete = connection.prepareStatement(DELETE)) {
            for (ShopChange change : changes) {
                BlockPosition position = change.position();
                if (change.shop().isPresent()) {
                    Shop shop = change.shop().get();
                    bindPosition(upsert, position);
                    upsert.setString(5, ActorCodec.kindOf(shop.owner()));
                    upsert.setString(6, shop.owner().uuid().toString());
                    upsert.setString(7, shop.commodity().id());
                    upsert.setLong(8, shop.price().amount());
                    upsert.setString(9, shop.price().currency().code());
                    upsert.setLong(10, shop.openedOn().value());
                    upsert.executeUpdate();
                } else {
                    bindPosition(delete, position);
                    delete.executeUpdate();
                }
            }
        }
    }

    private static void bindPosition(PreparedStatement statement, BlockPosition position) throws SQLException {
        statement.setString(1, position.dimension().value());
        statement.setInt(2, position.x());
        statement.setInt(3, position.y());
        statement.setInt(4, position.z());
    }

    ShopRegistry load(Connection connection) throws SQLException {
        List<Shop> shops = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT dimension, x, y, z, owner_kind, owner_id, commodity, price, currency, opened_on
                FROM shop ORDER BY seq""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                shops.add(new Shop(
                        new BlockPosition(new DimensionId(rows.getString("dimension")), rows.getInt("x"), rows.getInt("y"),
                                rows.getInt("z")),
                        ActorCodec.decode(rows.getString("owner_kind"), rows.getString("owner_id")),
                        Commodity.of(rows.getString("commodity")),
                        Money.of(rows.getLong("price"), new CurrencyId(rows.getString("currency"))),
                        Day.of(rows.getLong("opened_on"))));
            }
        }
        return ShopRegistry.restore(shops);
    }
}
