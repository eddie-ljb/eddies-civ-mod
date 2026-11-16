package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Ein Bund kann nicht in sich selbst oder einem seiner Glieder Mitglied werden.
 */
public final class FederationCycleException extends DomainException {

    public FederationCycleException(PolityName constituent, PolityName federation) {
        super(ErrorCode.FEDERATION_CYCLE, constituent.value(), federation.value());
    }
}
