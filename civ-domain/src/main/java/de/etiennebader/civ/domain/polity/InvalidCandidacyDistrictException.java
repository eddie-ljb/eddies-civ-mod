package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Bei einer Wahlkreiswahl fehlt der Wahlkreis der Kandidatur oder er gehört nicht zur wählenden Polity (#47).
 */
public final class InvalidCandidacyDistrictException extends DomainException {

    public InvalidCandidacyDistrictException(String district) {
        super(ErrorCode.INVALID_CANDIDACY_DISTRICT, district);
    }
}
