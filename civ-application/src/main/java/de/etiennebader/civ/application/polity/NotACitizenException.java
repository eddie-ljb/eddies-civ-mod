package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Nur Bürger einer Gemeinde dürfen das, z. B. ihr Recht ändern.
 */
public final class NotACitizenException extends DomainException {

    public NotACitizenException(ActorRef actor, String polityName) {
        super(ErrorCode.NOT_A_CITIZEN, actor, polityName);
    }
}
