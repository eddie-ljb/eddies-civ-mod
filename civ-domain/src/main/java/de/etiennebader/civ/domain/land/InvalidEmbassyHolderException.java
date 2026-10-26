package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Eine Botschaft kann nur an eine fremde Polity vergeben werden.
 */
public final class InvalidEmbassyHolderException extends DomainException {

    public InvalidEmbassyHolderException(ActorRef holder) {
        super(ErrorCode.INVALID_EMBASSY_HOLDER, holder);
    }
}
