package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Diese Wahl gibt es nicht (mehr).
 */
public final class UnknownElectionException extends DomainException {

    public UnknownElectionException(ElectionId election) {
        super(ErrorCode.UNKNOWN_ELECTION, election);
    }
}
