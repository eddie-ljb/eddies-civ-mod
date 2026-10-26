package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das letzte Mitglied kann nicht austreten, eine Polity ohne Mitglieder gibt es nicht.
 */
public final class LastMemberException extends DomainException {

    public LastMemberException(ActorRef member) {
        super(ErrorCode.LAST_MEMBER, member);
    }
}
