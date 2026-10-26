package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;

/**
 * Lesender Zugriff auf das Hauptbuch.
 */
public interface LedgerView {

    Money balanceOf(ActorRef account, CurrencyId currency);

    Money cashInCirculation(CurrencyId currency);

    Money totalSupply(CurrencyId currency);

    Money totalDeposits(CurrencyId currency);
}
