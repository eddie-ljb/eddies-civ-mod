/**
 * Servermarkt: Jedes Item lässt sich an den Server verkaufen und, solange er es auf Lager hat,
 * wieder kaufen. Preise ergeben sich aus einem Grundpreis, dem Lagerbestand (Angebot und
 * Nachfrage) und dem allgemeinen Preisniveau (Inflation durch wachsende Geldmenge).
 *
 * <p>Der Markt bucht selbst kein Geld; Erlös und Kaufpreis bucht die Anwendung im {@code Ledger}.
 *
 * <p>Darf verwenden: {@code shared}, {@code actor}.
 */
package de.etiennebader.civ.domain.market;
