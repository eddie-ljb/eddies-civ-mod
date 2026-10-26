package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;

/**
 * Es soll mehr Bargeld eingezahlt werden, als überhaupt im Umlauf ist. Hinweis auf Item-Duplikation.
 */
public final class CashIntegrityException extends DomainException {

    public CashIntegrityException(Money deposit, Money cashInCirculation) {
        super(ErrorCode.CASH_INTEGRITY, deposit, cashInCirculation);
    }
}
