package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das Parlament wird gewählt, nicht ernannt.
 */
public final class ParliamentNotAppointableException extends DomainException {

    public ParliamentNotAppointableException() {
        super(ErrorCode.PARLIAMENT_NOT_APPOINTABLE);
    }
}
