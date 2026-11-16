package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Gliedkörperschaft gehört schon einem Bund an; erst austreten.
 */
public final class AlreadyInFederationException extends DomainException {

    public AlreadyInFederationException(PolityName constituent, PolityName federation) {
        super(ErrorCode.ALREADY_IN_FEDERATION, constituent.value(), federation.value());
    }
}
