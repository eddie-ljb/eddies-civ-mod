package de.etiennebader.civ.application.party;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur gehört bereits einer Partei an.
 */
public final class AlreadyInPartyException extends DomainException {

    public AlreadyInPartyException(PolityName party) {
        super(ErrorCode.ALREADY_IN_PARTY, party.value());
    }
}
