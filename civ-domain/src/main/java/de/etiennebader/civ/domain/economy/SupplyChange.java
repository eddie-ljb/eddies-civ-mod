package de.etiennebader.civ.domain.economy;

/**
 * Grund, aus dem Geld geschöpft oder vernichtet wird.
 */
public enum SupplyChange {
    /** Smaragde wurden eingezahlt und als Buchgeld gutgeschrieben. */
    COMMODITY_DEPOSIT,
    /** Buchgeld wurde in Smaragde zurückgetauscht. */
    COMMODITY_WITHDRAWAL,
    /** Waren wurden an den Servermarkt verkauft; der Erlös entsteht neu. */
    MARKET_SALE,
    /** Waren wurden vom Servermarkt gekauft; der Preis verschwindet aus dem Umlauf. */
    MARKET_PURCHASE,
    /** Wirtschaftsleistung der Dorfbewohner einer Gemeinde, gutgeschrieben in der Gemeindekasse. */
    VILLAGER_ECONOMY,
    /** Eingriff eines Administrators. */
    ADMIN
}
