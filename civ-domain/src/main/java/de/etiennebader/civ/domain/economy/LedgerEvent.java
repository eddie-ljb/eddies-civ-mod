package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.Money;

/**
 * Alle Events des {@link Ledger}-Aggregats.
 */
public sealed interface LedgerEvent extends DomainEvent
        permits FundsTransferred, CashWithdrawn, CashDeposited, MoneyMinted, MoneyBurned {

    Money amount();
}
