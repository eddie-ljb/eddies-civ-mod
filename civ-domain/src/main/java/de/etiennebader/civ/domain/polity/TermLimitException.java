package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Amtszeitbegrenzung: Der Amtsinhaber hatte das Amt schon so oft in Folge, wie das Recht erlaubt.
 */
public final class TermLimitException extends DomainException {

    public TermLimitException(ActorRef actor, long terms) {
        super(ErrorCode.TERM_LIMIT_REACHED, actor, terms);
    }
}
