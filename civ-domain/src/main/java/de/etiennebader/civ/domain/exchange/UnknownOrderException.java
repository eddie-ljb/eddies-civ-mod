package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

public final class UnknownOrderException extends DomainException {

    public UnknownOrderException(Object order) {
        super(ErrorCode.UNKNOWN_ORDER, order);
    }
}
