package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Code oder Symbol der neuen Währung ist schon vergeben. */
public final class CurrencyAlreadyExistsException extends DomainException {

    public CurrencyAlreadyExistsException(String codeOrSymbol) {
        super(ErrorCode.CURRENCY_ALREADY_EXISTS, codeOrSymbol);
    }
}
