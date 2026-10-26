package de.etiennebader.civ.domain.economy;

/**
 * Grund, aus dem Geld geschöpft oder vernichtet wird.
 */
public enum SupplyChange {
    /** Smaragde wurden eingezahlt und als Buchgeld gutgeschrieben. */
    COMMODITY_DEPOSIT,
    /** Buchgeld wurde in Smaragde zurückgetauscht. */
    COMMODITY_WITHDRAWAL,
    /** Eingriff eines Administrators. */
    ADMIN
}
