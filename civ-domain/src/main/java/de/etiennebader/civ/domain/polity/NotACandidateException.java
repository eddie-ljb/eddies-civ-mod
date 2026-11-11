package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Für diesen Akteur kann man nicht stimmen: er kandidiert nicht.
 */
public final class NotACandidateException extends DomainException {

    public NotACandidateException(ActorRef actor) {
        super(ErrorCode.NOT_A_CANDIDATE, actor);
    }
}
