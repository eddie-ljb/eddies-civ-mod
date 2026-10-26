package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.StatuteId;

/**
 * Ein Gesetz soll aufgehoben werden, das nicht gilt.
 */
public final class UnknownStatuteException extends DomainException {

    public UnknownStatuteException(StatuteId statuteId) {
        super(ErrorCode.UNKNOWN_STATUTE, statuteId);
    }
}
