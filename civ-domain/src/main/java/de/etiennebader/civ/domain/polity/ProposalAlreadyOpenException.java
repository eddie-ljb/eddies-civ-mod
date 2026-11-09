package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Über diese Regelung wird bereits abgestimmt.
 */
public final class ProposalAlreadyOpenException extends DomainException {

    public ProposalAlreadyOpenException(String parameter) {
        super(ErrorCode.PROPOSAL_ALREADY_OPEN, parameter);
    }
}
