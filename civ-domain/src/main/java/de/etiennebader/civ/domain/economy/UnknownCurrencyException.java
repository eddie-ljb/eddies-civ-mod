package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Diese Währung ist nicht registriert. */
public final class UnknownCurrencyException extends DomainException {

    public UnknownCurrencyException(CurrencyId currency) {
        super(ErrorCode.UNKNOWN_CURRENCY, currency);
    }
}
