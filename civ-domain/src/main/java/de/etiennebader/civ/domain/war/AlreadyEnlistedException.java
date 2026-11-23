package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

/** Der Akteur dient bereits in der Garnison dieser Polity. */
public final class AlreadyEnlistedException extends DomainException {

    public AlreadyEnlistedException(PolityId polity, ActorRef member) {
        super(ErrorCode.ALREADY_ENLISTED, polity, member);
    }
}
