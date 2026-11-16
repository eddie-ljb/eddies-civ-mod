package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

public final class NotEnoughSharesException extends DomainException {

    public NotEnoughSharesException(Object company) {
        super(ErrorCode.NOT_ENOUGH_SHARES, company);
    }
}
