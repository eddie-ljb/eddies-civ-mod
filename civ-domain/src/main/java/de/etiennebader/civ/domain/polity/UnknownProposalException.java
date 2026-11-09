package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.ProposalId;

/**
 * Diese Abstimmung gibt es nicht (mehr).
 */
public final class UnknownProposalException extends DomainException {

    public UnknownProposalException(ProposalId proposal) {
        super(ErrorCode.UNKNOWN_PROPOSAL, proposal);
    }
}
