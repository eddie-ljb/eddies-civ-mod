package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.StateDelta;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.exchange.Holding;
import de.etiennebader.civ.domain.exchange.Listing;
import de.etiennebader.civ.domain.exchange.Order;
import de.etiennebader.civ.domain.exchange.Quote;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.exchange.Trade;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.media.Press;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.shared.ArticleId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.OrderId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabellen der Börse ({@code stock_*}, {@code dividend}) und der Presse ({@code article}). Ändert sich etwas, werden
 * Börse bzw. Presse vollständig neu geschrieben; beide sind begrenzt.
 */
final class MarketPressTables {

    void writeExchange(Connection connection, StateDelta.ExchangeSnapshot exchange) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            for (String table : List.of("stock_listing", "stock_holding", "stock_order", "stock_trade", "stock_quote",
                    "dividend")) {
                clear.executeUpdate("DELETE FROM " + table);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO stock_listing (company_id, total_shares, initial_price, listed_on) VALUES (?, ?, ?, ?)")) {
            for (Listing listing : exchange.listings()) {
                insert.setString(1, listing.company().value().toString());
                insert.setLong(2, listing.totalShares());
                insert.setLong(3, listing.initialPrice());
                insert.setLong(4, listing.listedOn().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO stock_holding (company_id, holder_kind, holder_id, shares) VALUES (?, ?, ?, ?)")) {
            for (Holding holding : exchange.holdings()) {
                insert.setString(1, holding.company().value().toString());
                insert.setString(2, ActorCodec.kindOf(holding.holder()));
                insert.setString(3, holding.holder().uuid().toString());
                insert.setLong(4, holding.shares());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO stock_order (id, number, company_id, trader_kind, trader_id, side, quantity, remaining,
                    limit_price, placed_on) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            for (Order order : exchange.orders()) {
                insert.setString(1, order.id().value().toString());
                insert.setInt(2, order.number());
                insert.setString(3, order.company().value().toString());
                insert.setString(4, ActorCodec.kindOf(order.trader()));
                insert.setString(5, order.trader().uuid().toString());
                insert.setString(6, order.side().name());
                insert.setLong(7, order.quantity());
                insert.setLong(8, order.remaining());
                insert.setLong(9, order.limit());
                insert.setLong(10, order.placedOn().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO stock_trade (position, company_id, buyer_kind, buyer_id, seller_kind, seller_id, quantity,
                    price, day) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            int position = 0;
            for (Trade trade : exchange.trades()) {
                insert.setInt(1, position++);
                insert.setString(2, trade.company().value().toString());
                insert.setString(3, ActorCodec.kindOf(trade.buyer()));
                insert.setString(4, trade.buyer().uuid().toString());
                insert.setString(5, ActorCodec.kindOf(trade.seller()));
                insert.setString(6, trade.seller().uuid().toString());
                insert.setLong(7, trade.quantity());
                insert.setLong(8, trade.price());
                insert.setLong(9, trade.day().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO stock_quote (company_id, day, open, high, low, close, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
            for (Map.Entry<PolityId, List<Quote>> entry : exchange.quotes().entrySet()) {
                for (Quote quote : entry.getValue()) {
                    insert.setString(1, entry.getKey().value().toString());
                    insert.setLong(2, quote.day().value());
                    insert.setLong(3, quote.open());
                    insert.setLong(4, quote.high());
                    insert.setLong(5, quote.low());
                    insert.setLong(6, quote.close());
                    insert.setLong(7, quote.volume());
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO dividend (position, company_id, per_share, total, day) VALUES (?, ?, ?, ?, ?)")) {
            int position = 0;
            for (Dividend dividend : exchange.dividends()) {
                insert.setInt(1, position++);
                insert.setString(2, dividend.company().value().toString());
                insert.setLong(3, dividend.perShare());
                insert.setLong(4, dividend.total());
                insert.setLong(5, dividend.day().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    StockExchange loadExchange(Connection connection) throws SQLException {
        List<Listing> listings = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM stock_listing ORDER BY listed_on, company_id");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                listings.add(new Listing(polity(rows.getString("company_id")), rows.getLong("total_shares"),
                        rows.getLong("initial_price"), Day.of(rows.getLong("listed_on"))));
            }
        }
        List<Holding> holdings = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM stock_holding");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                holdings.add(new Holding(polity(rows.getString("company_id")),
                        ActorCodec.decode(rows.getString("holder_kind"), rows.getString("holder_id")),
                        rows.getLong("shares")));
            }
        }
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM stock_order ORDER BY number");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                orders.add(new Order(new OrderId(UUID.fromString(rows.getString("id"))), rows.getInt("number"),
                        polity(rows.getString("company_id")),
                        ActorCodec.decode(rows.getString("trader_kind"), rows.getString("trader_id")),
                        Side.valueOf(rows.getString("side")), rows.getLong("quantity"), rows.getLong("remaining"),
                        rows.getLong("limit_price"), Day.of(rows.getLong("placed_on"))));
            }
        }
        List<Trade> trades = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM stock_trade ORDER BY position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                trades.add(new Trade(polity(rows.getString("company_id")),
                        ActorCodec.decode(rows.getString("buyer_kind"), rows.getString("buyer_id")),
                        ActorCodec.decode(rows.getString("seller_kind"), rows.getString("seller_id")),
                        rows.getLong("quantity"), rows.getLong("price"), Day.of(rows.getLong("day"))));
            }
        }
        Map<PolityId, List<Quote>> quotes = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM stock_quote ORDER BY company_id, day");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                quotes.computeIfAbsent(polity(rows.getString("company_id")), ignored -> new ArrayList<>())
                        .add(new Quote(Day.of(rows.getLong("day")), rows.getLong("open"), rows.getLong("high"),
                                rows.getLong("low"), rows.getLong("close"), rows.getLong("volume")));
            }
        }
        List<Dividend> dividends = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM dividend ORDER BY position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                dividends.add(new Dividend(polity(rows.getString("company_id")), rows.getLong("per_share"),
                        rows.getLong("total"), Day.of(rows.getLong("day"))));
            }
        }
        return StockExchange.restore(listings, holdings, orders, trades, quotes, dividends);
    }

    void writePress(Connection connection, List<Article> articles) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM article");
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO article (id, number, newspaper_id, author_kind, author_id, title, body, topic, subject_kind,
                    subject_id, tendency, day, towns, readers) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            for (Article article : articles) {
                insert.setString(1, article.id().value().toString());
                insert.setInt(2, article.number());
                insert.setString(3, article.newspaper().value().toString());
                insert.setString(4, ActorCodec.kindOf(article.author()));
                insert.setString(5, article.author().uuid().toString());
                insert.setString(6, article.title());
                insert.setString(7, article.body());
                insert.setString(8, article.topic().name());
                if (article.subject().isPresent()) {
                    insert.setString(9, ActorCodec.kindOf(article.subject().get()));
                    insert.setString(10, article.subject().get().uuid().toString());
                } else {
                    insert.setNull(9, Types.VARCHAR);
                    insert.setNull(10, Types.VARCHAR);
                }
                insert.setInt(11, article.tendency());
                insert.setLong(12, article.day().value());
                insert.setString(13, String.join(",", article.towns().stream().map(id -> id.value().toString())
                        .toList()));
                insert.setInt(14, article.readers());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    Press loadPress(Connection connection) throws SQLException {
        List<Article> articles = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM article ORDER BY number");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String subjectKind = rows.getString("subject_kind");
                Optional<ActorRef> subject = subjectKind == null ? Optional.empty()
                        : Optional.of(ActorCodec.decode(subjectKind, rows.getString("subject_id")));
                String towns = rows.getString("towns");
                List<PolityId> townIds = towns.isEmpty() ? List.of()
                        : Arrays.stream(towns.split(",")).map(MarketPressTables::polity).toList();
                articles.add(new Article(new ArticleId(UUID.fromString(rows.getString("id"))), rows.getInt("number"),
                        polity(rows.getString("newspaper_id")),
                        ActorCodec.decode(rows.getString("author_kind"), rows.getString("author_id")),
                        rows.getString("title"), rows.getString("body"), Platform.valueOf(rows.getString("topic")),
                        subject, rows.getInt("tendency"), Day.of(rows.getLong("day")), townIds,
                        rows.getInt("readers")));
            }
        }
        return Press.restore(articles);
    }

    private static PolityId polity(String id) {
        return new PolityId(UUID.fromString(id));
    }
}
