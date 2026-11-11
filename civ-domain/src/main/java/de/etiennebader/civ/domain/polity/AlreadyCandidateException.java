package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur kandidiert bereits.
 */
public final class AlreadyCandidateException extends DomainException {

    public AlreadyCandidateException(ActorRef actor) {
        super(ErrorCode.ALREADY_CANDIDATE, actor);
    }
}
