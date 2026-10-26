package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.application.persistence.AccountKey;
import de.etiennebader.civ.domain.economy.AccountBalance;
import de.etiennebader.civ.domain.economy.CurrencyTotals;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabellen {@code account_balance} (nur Konten ungleich 0) und {@code currency_circulation}.
 */
final class LedgerTable {

    private static final String UPSERT_BALANCE = """
            INSERT INTO account_balance (actor_kind, actor_id, currency, amount) VALUES (?, ?, ?, ?)
            ON CONFLICT (actor_kind, actor_id, currency) DO UPDATE SET amount = excluded.amount""";
    private static final String DELETE_BALANCE =
            "DELETE FROM account_balance WHERE actor_kind = ? AND actor_id = ? AND currency = ?";
    private static final String UPSERT_TOTALS = """
            INSERT INTO currency_circulation (currency, supply, cash) VALUES (?, ?, ?)
            ON CONFLICT (currency) DO UPDATE SET supply = excluded.supply, cash = excluded.cash""";

    void writeAccounts(Connection connection, Iterable<AccountKey> accounts, LedgerView ledger) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT_BALANCE);
             PreparedStatement delete = connection.prepareStatement(DELETE_BALANCE)) {
            for (AccountKey key : accounts) {
                Money balance = ledger.balanceOf(key.account(), key.currency());
                PreparedStatement statement = balance.isZero() ? delete : upsert;
                statement.setString(1, ActorCodec.kindOf(key.account()));
                statement.setString(2, key.account().uuid().toString());
                statement.setString(3, key.currency().code());
                if (!balance.isZero()) {
                    statement.setLong(4, balance.amount());
                }
                statement.addBatch();
            }
            delete.executeBatch();
            upsert.executeBatch();
        }
    }

    void writeTotals(Connection connection, Iterable<CurrencyId> currencies, LedgerView ledger) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT_TOTALS)) {
            for (CurrencyId currency : currencies) {
                upsert.setString(1, currency.code());
                upsert.setLong(2, ledger.totalSupply(currency).amount());
                upsert.setLong(3, ledger.cashInCirculation(currency).amount());
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
    }

    List<AccountBalance> loadBalances(Connection connection) throws SQLException {
        List<AccountBalance> balances = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT actor_kind, actor_id, currency, amount FROM account_balance");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                balances.add(new AccountBalance(ActorCodec.decode(rows.getString("actor_kind"), rows.getString("actor_id")),
                        Money.of(rows.getLong("amount"), new CurrencyId(rows.getString("currency")))));
            }
        }
        return balances;
    }

    List<CurrencyTotals> loadTotals(Connection connection) throws SQLException {
        List<CurrencyTotals> totals = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT currency, supply, cash FROM currency_circulation ORDER BY currency");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                CurrencyId currency = new CurrencyId(rows.getString("currency"));
                totals.add(new CurrencyTotals(currency, Money.of(rows.getLong("supply"), currency),
                        Money.of(rows.getLong("cash"), currency)));
            }
        }
        return totals;
    }
}
