package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Ein Gericht hat den Akteur verbannt; er darf bis zum genannten Tag nicht beitreten. */
public final class ExiledException extends DomainException {

    public ExiledException(ActorRef actor, Day until) {
        super(ErrorCode.EXILED, actor, until.value());
    }
}
