package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

/** Neutralität lässt sich nicht während eines laufenden Kriegs erkaufen. */
public final class AtWarException extends DomainException {

    public AtWarException(PolityId polity) {
        super(ErrorCode.AT_WAR, polity);
    }
}
