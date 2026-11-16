package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Den Vertrag gibt es nicht.
 */
public final class UnknownContractException extends DomainException {

    public UnknownContractException(Object contract) {
        super(ErrorCode.UNKNOWN_CONTRACT, contract);
    }
}
