package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das letzte Glied kann nicht austreten; der Bund muss aufgelöst werden.
 */
public final class LastConstituentException extends DomainException {

    public LastConstituentException(PolityName federation) {
        super(ErrorCode.LAST_CONSTITUENT, federation.value());
    }
}
