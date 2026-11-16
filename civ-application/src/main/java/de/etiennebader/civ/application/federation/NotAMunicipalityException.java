package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Polity ist keine Gemeinde; einzelne Personen treten nur Gemeinden bei.
 */
public final class NotAMunicipalityException extends DomainException {

    public NotAMunicipalityException(PolityName polity) {
        super(ErrorCode.NOT_A_MUNICIPALITY, polity.value());
    }
}
