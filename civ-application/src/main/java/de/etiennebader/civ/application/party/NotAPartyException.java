package de.etiennebader.civ.application.party;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Polity ist keine Partei.
 */
public final class NotAPartyException extends DomainException {

    public NotAPartyException(PolityName polity) {
        super(ErrorCode.NOT_A_PARTY, polity.value());
    }
}
