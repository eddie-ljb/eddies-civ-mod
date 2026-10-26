package de.etiennebader.civ.application.support;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

/**
 * Die angegebene Polity existiert nicht.
 */
public final class UnknownPolityException extends DomainException {

    public UnknownPolityException(PolityId polity) {
        super(ErrorCode.UNKNOWN_POLITY, polity);
    }
}
