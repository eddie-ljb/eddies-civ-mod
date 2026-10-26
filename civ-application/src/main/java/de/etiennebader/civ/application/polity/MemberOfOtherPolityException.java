package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur gehört bereits einer anderen Gemeinde an.
 */
public final class MemberOfOtherPolityException extends DomainException {

    public MemberOfOtherPolityException(ActorRef actor, Polity otherPolity) {
        super(ErrorCode.MEMBER_OF_OTHER_POLITY, actor, otherPolity.name().value());
    }
}
