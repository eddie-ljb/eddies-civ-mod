package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

public final class InvalidOrderException extends DomainException {

    public InvalidOrderException(Object reason) {
        super(ErrorCode.INVALID_ORDER, reason);
    }
}
