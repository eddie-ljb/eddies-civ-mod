package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Vertrag erlaubt das gerade nicht, z. B. Annehmen eines schon geltenden Vertrags, oder der Akteur darf es nicht.
 */
public final class ContractStateException extends DomainException {

    public ContractStateException(int number) {
        super(ErrorCode.CONTRACT_NOT_POSSIBLE, number);
    }
}
