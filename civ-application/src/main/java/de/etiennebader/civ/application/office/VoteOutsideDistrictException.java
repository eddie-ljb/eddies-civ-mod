package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Bei einer Wahlkreiswahl darf man nur Kandidaten des eigenen Wahlkreises wählen (#47).
 */
public final class VoteOutsideDistrictException extends DomainException {

    public VoteOutsideDistrictException(ActorRef candidate) {
        super(ErrorCode.VOTE_OUTSIDE_DISTRICT, candidate);
    }
}
