package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

public final class OutsidePartyAreaException extends DomainException {

    public OutsidePartyAreaException(String party, PolityId area) {
        super(ErrorCode.CANDIDACY_OUTSIDE_PARTY_AREA, party, area);
    }
}
