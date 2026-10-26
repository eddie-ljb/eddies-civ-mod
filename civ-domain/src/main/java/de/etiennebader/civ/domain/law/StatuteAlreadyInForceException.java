package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.StatuteId;

/**
 * Ein Gesetz soll erlassen werden, das bereits gilt.
 */
public final class StatuteAlreadyInForceException extends DomainException {

    public StatuteAlreadyInForceException(StatuteId statuteId) {
        super(ErrorCode.STATUTE_ALREADY_IN_FORCE, statuteId);
    }
}
