package de.etiennebader.civ.application.party;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur gehört keiner Partei an.
 */
public final class NotInPartyException extends DomainException {

    public NotInPartyException() {
        super(ErrorCode.NOT_IN_PARTY);
    }
}
