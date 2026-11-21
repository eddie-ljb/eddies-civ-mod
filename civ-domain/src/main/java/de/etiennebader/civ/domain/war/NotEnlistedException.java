package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

/** Der Akteur dient nicht in der Garnison dieser Polity. */
public final class NotEnlistedException extends DomainException {

    public NotEnlistedException(PolityId polity, ActorRef member) {
        super(ErrorCode.NOT_ENLISTED, polity, member);
    }
}
