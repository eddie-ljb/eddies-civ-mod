package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Dieser Krieg ist bereits beendet. */
public final class WarAlreadyEndedException extends DomainException {

    public WarAlreadyEndedException(WarId war) {
        super(ErrorCode.WAR_ALREADY_ENDED, war);
    }
}
