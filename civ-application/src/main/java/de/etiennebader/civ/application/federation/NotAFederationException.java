package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Polity ist kein Bund.
 */
public final class NotAFederationException extends DomainException {

    public NotAFederationException(PolityName polity) {
        super(ErrorCode.NOT_A_FEDERATION, polity.value());
    }
}
