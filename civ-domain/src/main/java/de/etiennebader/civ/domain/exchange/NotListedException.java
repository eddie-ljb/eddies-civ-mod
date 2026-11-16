package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

public final class NotListedException extends DomainException {

    public NotListedException(Object company) {
        super(ErrorCode.NOT_LISTED, company);
    }
}
