package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Die eingebaute Leitwährung (Smaragd) lässt sich nicht verändern. */
public final class CurrencyProtectedException extends DomainException {

    public CurrencyProtectedException(CurrencyId currency) {
        super(ErrorCode.CURRENCY_PROTECTED, currency);
    }
}
