package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Eine Polity kann nicht von einer anderen Polity gegründet werden.
 */
public final class IllegalFounderException extends DomainException {

    public IllegalFounderException(ActorRef founder) {
        super(ErrorCode.ILLEGAL_FOUNDER, founder);
    }
}
