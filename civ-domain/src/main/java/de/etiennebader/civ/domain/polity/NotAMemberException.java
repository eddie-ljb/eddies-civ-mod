package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur ist kein Mitglied der Polity.
 */
public final class NotAMemberException extends DomainException {

    public NotAMemberException(ActorRef actor) {
        super(ErrorCode.NOT_A_MEMBER, actor);
    }
}
