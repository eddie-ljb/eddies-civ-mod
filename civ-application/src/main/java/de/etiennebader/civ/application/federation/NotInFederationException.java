package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Polity gehört keinem Bund an.
 */
public final class NotInFederationException extends DomainException {

    public NotInFederationException(PolityName polity) {
        super(ErrorCode.NOT_IN_FEDERATION, polity.value());
    }
}
