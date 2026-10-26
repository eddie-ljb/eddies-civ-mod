/**
 * Geld: alle Konten und der Bargeldumlauf im {@link de.etiennebader.civ.domain.economy.Ledger}.
 *
 * <p>Zentrale Invariante je Währung: Summe aller Kontostände + umlaufendes Bargeld = Geldmenge.
 *
 * <p>Darf verwenden: {@code shared}, {@code actor}.
 */
package de.etiennebader.civ.domain.economy;
