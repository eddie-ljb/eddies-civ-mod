package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Mit gewähltem Parlament stimmen nur Abgeordnete ab.
 */
public final class NotInParliamentException extends DomainException {

    public NotInParliamentException(ActorRef actor) {
        super(ErrorCode.NOT_IN_PARLIAMENT, actor);
    }
}
