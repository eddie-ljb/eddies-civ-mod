package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Diesen Krieg gibt es nicht. */
public final class UnknownWarException extends DomainException {

    public UnknownWarException(WarId war) {
        super(ErrorCode.UNKNOWN_WAR, war);
    }
}
