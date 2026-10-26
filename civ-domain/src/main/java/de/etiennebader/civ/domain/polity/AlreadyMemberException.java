package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur ist bereits Mitglied der Polity.
 */
public final class AlreadyMemberException extends DomainException {

    public AlreadyMemberException(ActorRef member) {
        super(ErrorCode.ALREADY_MEMBER, member);
    }
}
