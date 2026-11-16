package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das Recht schreibt die Stimmabgabe an einer Wahlurne vor.
 */
public final class BallotBoxRequiredException extends DomainException {

    public BallotBoxRequiredException() {
        super(ErrorCode.BALLOT_BOX_REQUIRED);
    }
}
