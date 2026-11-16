package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.StateDelta;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.community.ChatMessage;
import de.etiennebader.civ.domain.community.Community;
import de.etiennebader.civ.domain.community.Letter;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.contract.ContractRegistry;
import de.etiennebader.civ.domain.contract.ContractStatus;
import de.etiennebader.civ.domain.contract.EndReason;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.LetterId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabellen {@code contract}, {@code letter}, {@code friendship} und {@code chat_message}. Ändert sich etwas, werden
 * Verträge bzw. die Gemeinschaft vollständig neu geschrieben; beide sind klein.
 */
final class CivilTables {

    void writeContracts(Connection connection, List<Contract> contracts) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM contract");
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO contract (id, number, kind, offeror_kind, offeror_id, counterparty_kind, counterparty_id,
                    payer_is_offeror, currency, per_day, once, deposit, plot_polity_id, plot_id, title, text, term_days,
                    offered_on, started_on, ends_on, status, end_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            for (Contract contract : contracts) {
                insert.setString(1, contract.id().value().toString());
                insert.setInt(2, contract.number());
                insert.setString(3, contract.kind().name());
                insert.setString(4, ActorCodec.kindOf(contract.offeror()));
                insert.setString(5, contract.offeror().uuid().toString());
                insert.setString(6, ActorCodec.kindOf(contract.counterparty()));
                insert.setString(7, contract.counterparty().uuid().toString());
                insert.setInt(8, contract.payer().equals(contract.offeror()) ? 1 : 0);
                insert.setString(9, contract.perDay().currency().code());
                insert.setLong(10, contract.perDay().amount());
                insert.setLong(11, contract.once().amount());
                insert.setLong(12, contract.deposit().amount());
                if (contract.plot().isPresent()) {
                    insert.setString(13, contract.plot().get().polity().value().toString());
                    insert.setString(14, contract.plot().get().plot().value().toString());
                } else {
                    insert.setNull(13, Types.VARCHAR);
                    insert.setNull(14, Types.VARCHAR);
                }
                insert.setString(15, contract.title());
                insert.setString(16, contract.text());
                insert.setLong(17, contract.termDays());
                insert.setLong(18, contract.offeredOn().value());
                bindDay(insert, 19, contract.startedOn());
                bindDay(insert, 20, contract.endsOn());
                insert.setString(21, contract.status().name());
                if (contract.endReason().isPresent()) {
                    insert.setString(22, contract.endReason().get().name());
                } else {
                    insert.setNull(22, Types.VARCHAR);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    ContractRegistry loadContracts(Connection connection) throws SQLException {
        List<Contract> contracts = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM contract ORDER BY number");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                ActorRef offeror = ActorCodec.decode(rows.getString("offeror_kind"), rows.getString("offeror_id"));
                ActorRef counterparty = ActorCodec.decode(rows.getString("counterparty_kind"),
                        rows.getString("counterparty_id"));
                boolean offerorPays = rows.getInt("payer_is_offeror") == 1;
                CurrencyId currency = new CurrencyId(rows.getString("currency"));
                String plotPolity = rows.getString("plot_polity_id");
                Optional<Contract.PlotRef> plot = plotPolity == null ? Optional.empty()
                        : Optional.of(new Contract.PlotRef(new PolityId(UUID.fromString(plotPolity)),
                        new PlotId(UUID.fromString(rows.getString("plot_id")))));
                String endReason = rows.getString("end_reason");
                contracts.add(new Contract(new ContractId(UUID.fromString(rows.getString("id"))), rows.getInt("number"),
                        ContractKind.valueOf(rows.getString("kind")), offeror, counterparty,
                        offerorPays ? offeror : counterparty, offerorPays ? counterparty : offeror,
                        Money.of(rows.getLong("per_day"), currency), Money.of(rows.getLong("once"), currency),
                        Money.of(rows.getLong("deposit"), currency), plot, rows.getString("title"),
                        rows.getString("text"), rows.getLong("term_days"), Day.of(rows.getLong("offered_on")),
                        dayOf(rows, "started_on"), dayOf(rows, "ends_on"),
                        ContractStatus.valueOf(rows.getString("status")),
                        endReason == null ? Optional.empty() : Optional.of(EndReason.valueOf(endReason))));
            }
        }
        return ContractRegistry.restore(contracts);
    }

    void writeCommunity(Connection connection, StateDelta.CommunitySnapshot community) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM letter");
            clear.executeUpdate("DELETE FROM friendship");
            clear.executeUpdate("DELETE FROM chat_message");
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO letter (id, position, from_id, to_id, subject, body, sent_on, deliver_on, read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            int position = 0;
            for (Letter letter : community.letters()) {
                insert.setString(1, letter.id().value().toString());
                insert.setInt(2, position++);
                insert.setString(3, letter.from().uuid().toString());
                insert.setString(4, letter.to().uuid().toString());
                insert.setString(5, letter.subject());
                insert.setString(6, letter.body());
                insert.setLong(7, letter.sentOn().value());
                insert.setLong(8, letter.deliverOn().value());
                insert.setInt(9, letter.read() ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO friendship (first_id, second_id, request) VALUES (?, ?, ?)")) {
            for (List<PlayerRef> pair : community.friendships()) {
                bindPair(insert, pair, 0);
            }
            for (List<PlayerRef> pair : community.requests()) {
                bindPair(insert, pair, 1);
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO chat_message (seq, from_id, to_id, text, at, origin) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (ChatMessage message : community.messages()) {
                insert.setLong(1, message.seq());
                insert.setString(2, message.from().uuid().toString());
                if (message.to().isPresent()) {
                    insert.setString(3, message.to().get().uuid().toString());
                } else {
                    insert.setNull(3, Types.VARCHAR);
                }
                insert.setString(4, message.text());
                insert.setLong(5, message.at());
                insert.setString(6, message.origin());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    Community loadCommunity(Connection connection) throws SQLException {
        List<Letter> letters = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM letter ORDER BY position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                letters.add(new Letter(new LetterId(UUID.fromString(rows.getString("id"))), player(rows, "from_id"),
                        player(rows, "to_id"), rows.getString("subject"), rows.getString("body"),
                        Day.of(rows.getLong("sent_on")), Day.of(rows.getLong("deliver_on")), rows.getInt("read") == 1));
            }
        }
        List<List<PlayerRef>> friendships = new ArrayList<>();
        List<List<PlayerRef>> requests = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM friendship");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                List<PlayerRef> pair = List.of(player(rows, "first_id"), player(rows, "second_id"));
                (rows.getInt("request") == 1 ? requests : friendships).add(pair);
            }
        }
        List<ChatMessage> messages = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM chat_message ORDER BY seq");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String to = rows.getString("to_id");
                messages.add(new ChatMessage(rows.getLong("seq"), player(rows, "from_id"),
                        to == null ? Optional.empty() : Optional.of(new PlayerRef(new PlayerId(UUID.fromString(to)))),
                        rows.getString("text"), rows.getLong("at"), rows.getString("origin")));
            }
        }
        return Community.restore(letters, friendships, requests, messages);
    }

    private static void bindPair(PreparedStatement insert, List<PlayerRef> pair, int request) throws SQLException {
        insert.setString(1, pair.get(0).uuid().toString());
        insert.setString(2, pair.get(1).uuid().toString());
        insert.setInt(3, request);
        insert.addBatch();
    }

    private static PlayerRef player(ResultSet rows, String column) throws SQLException {
        return new PlayerRef(new PlayerId(UUID.fromString(rows.getString(column))));
    }

    private static void bindDay(PreparedStatement statement, int index, Optional<Day> day) throws SQLException {
        if (day.isPresent()) {
            statement.setLong(index, day.get().value());
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private static Optional<Day> dayOf(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? Optional.empty() : Optional.of(Day.of(value));
    }
}
