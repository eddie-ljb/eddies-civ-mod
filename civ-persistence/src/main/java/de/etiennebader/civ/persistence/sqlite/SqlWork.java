package de.etiennebader.civ.persistence.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Datenbankarbeit innerhalb einer Transaktion.
 *
 * @param <T> Ergebnistyp
 */
@FunctionalInterface
public interface SqlWork<T> {

    T execute(Connection connection) throws SQLException;
}
