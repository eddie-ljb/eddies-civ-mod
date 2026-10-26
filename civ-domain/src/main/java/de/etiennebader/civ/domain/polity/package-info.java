/**
 * Gemeinwesen: das {@link de.etiennebader.civ.domain.polity.Polity}-Aggregat mit Mitgliedern und geltendem Recht.
 *
 * <p>Kasse und Gebiet gehören bewusst nicht dazu, sie liegen in {@code economy.Ledger} und
 * {@code territory.TerritoryRegistry}, weil ihre Invarianten mehrere Polities betreffen.
 *
 * <p>Darf verwenden: {@code shared}, {@code actor}, {@code law}.
 */
package de.etiennebader.civ.domain.polity;
