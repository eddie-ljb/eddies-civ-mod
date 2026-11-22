package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.credit.CreditRegistry;
import de.etiennebader.civ.domain.credit.CreditView;
import de.etiennebader.civ.domain.credit.Loan;
import de.etiennebader.civ.domain.credit.LoanId;
import de.etiennebader.civ.domain.credit.LoanStatus;
import de.etiennebader.civ.domain.economy.Currency;
import de.etiennebader.civ.domain.economy.CurrencyRegistry;
import de.etiennebader.civ.domain.economy.CurrencyView;
import de.etiennebader.civ.domain.economy.ExchangeRate;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tabellen der Währungen, Banken, Kreditscores und Kredite; die Register werden als Ganzes geschrieben. */
final class CurrencyCreditTables {

    void writeCurrencies(Connection connection, CurrencyView currencies) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM currency");
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO currency (code, name, symbol, rate_numerator, rate_denominator, issuer, chair,
                    base_rate_percent, item_id, seq) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            int seq = 0;
            for (Currency currency : currencies.all()) {
                if (currency.id().equals(CurrencyId.EMERALD)) {
                    continue;
                }
                insert.setString(1, currency.id().code());
                insert.setString(2, currency.name());
                insert.setString(3, currency.symbol());
                insert.setLong(4, currency.rateToEmerald().numerator());
                insert.setLong(5, currency.rateToEmerald().denominator());
                insert.setString(6, currency.issuer().map(ActorCodec::encode).orElse(null));
                insert.setString(7, currency.centralBankChair().map(ActorCodec::encode).orElse(null));
                insert.setInt(8, currency.baseRatePercent());
                insert.setString(9, currency.itemId().orElse(null));
                insert.setInt(10, seq++);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    CurrencyRegistry loadCurrencies(Connection connection) throws SQLException {
        List<Currency> currencies = new ArrayList<>();
        try (Statement select = connection.createStatement();
             ResultSet rows = select.executeQuery("""
                     SELECT code, name, symbol, rate_numerator, rate_denominator, issuer, chair, base_rate_percent,
                         item_id FROM currency ORDER BY seq""")) {
            while (rows.next()) {
                String issuer = rows.getString("issuer");
                String chair = rows.getString("chair");
                currencies.add(new Currency(new CurrencyId(rows.getString("code")), rows.getString("name"),
                        rows.getString("symbol"),
                        new ExchangeRate(rows.getLong("rate_numerator"), rows.getLong("rate_denominator")),
                        Optional.ofNullable(issuer).map(ActorCodec::decode),
                        Optional.ofNullable(chair).map(ActorCodec::decode), rows.getInt("base_rate_percent"),
                        Optional.ofNullable(rows.getString("item_id"))));
            }
        }
        return CurrencyRegistry.restore(currencies);
    }

    void writeCredit(Connection connection, CreditView credit) throws SQLException {
        try (Statement clear = connection.createStatement()) {
            clear.executeUpdate("DELETE FROM loan");
            clear.executeUpdate("DELETE FROM credit_score");
            clear.executeUpdate("DELETE FROM bank");
        }
        try (PreparedStatement bank = connection.prepareStatement("INSERT INTO bank (actor, seq) VALUES (?, ?)");
             PreparedStatement score = connection.prepareStatement(
                     "INSERT INTO credit_score (actor, score) VALUES (?, ?)");
             PreparedStatement loan = connection.prepareStatement("""
                     INSERT INTO loan (id, lender, borrower, currency, principal, rate_bp, interest, issued_on,
                         due_on, repaid, status, seq) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            int seq = 0;
            for (ActorRef actor : credit.banks().stream().sorted(ActorRef.CANONICAL_ORDER).toList()) {
                bank.setString(1, ActorCodec.encode(actor));
                bank.setInt(2, seq++);
                bank.addBatch();
            }
            for (Map.Entry<ActorRef, Integer> entry : credit.scores().entrySet()) {
                score.setString(1, ActorCodec.encode(entry.getKey()));
                score.setInt(2, entry.getValue());
                score.addBatch();
            }
            seq = 0;
            for (Loan found : credit.loans()) {
                loan.setString(1, found.id().value().toString());
                loan.setString(2, ActorCodec.encode(found.lender()));
                loan.setString(3, ActorCodec.encode(found.borrower()));
                loan.setString(4, found.currency().code());
                loan.setLong(5, found.principal());
                loan.setInt(6, found.rateBasisPoints());
                loan.setLong(7, found.interest());
                loan.setLong(8, found.issuedOn().value());
                loan.setLong(9, found.dueOn().value());
                loan.setLong(10, found.repaid());
                loan.setString(11, found.status().name());
                loan.setInt(12, seq++);
                loan.addBatch();
            }
            bank.executeBatch();
            score.executeBatch();
            loan.executeBatch();
        }
    }

    CreditRegistry loadCredit(Connection connection) throws SQLException {
        List<ActorRef> banks = new ArrayList<>();
        Map<ActorRef, Integer> scores = new LinkedHashMap<>();
        List<Loan> loans = new ArrayList<>();
        try (Statement select = connection.createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT actor FROM bank ORDER BY seq")) {
                while (rows.next()) {
                    banks.add(ActorCodec.decode(rows.getString("actor")));
                }
            }
            try (ResultSet rows = select.executeQuery("SELECT actor, score FROM credit_score")) {
                while (rows.next()) {
                    scores.put(ActorCodec.decode(rows.getString("actor")), rows.getInt("score"));
                }
            }
            try (ResultSet rows = select.executeQuery("""
                    SELECT id, lender, borrower, currency, principal, rate_bp, interest, issued_on, due_on, repaid,
                        status FROM loan ORDER BY seq""")) {
                while (rows.next()) {
                    loans.add(new Loan(new LoanId(UUID.fromString(rows.getString("id"))),
                            ActorCodec.decode(rows.getString("lender")), ActorCodec.decode(rows.getString("borrower")),
                            new CurrencyId(rows.getString("currency")), rows.getLong("principal"),
                            rows.getInt("rate_bp"), rows.getLong("interest"), Day.of(rows.getLong("issued_on")),
                            Day.of(rows.getLong("due_on")), rows.getLong("repaid"),
                            LoanStatus.valueOf(rows.getString("status"))));
                }
            }
        }
        return CreditRegistry.restore(banks, scores, loans);
    }
}
