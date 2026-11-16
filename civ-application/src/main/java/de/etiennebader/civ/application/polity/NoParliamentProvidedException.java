package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das Recht sieht kein Parlament vor.
 */
public final class NoParliamentProvidedException extends DomainException {

    public NoParliamentProvidedException() {
        super(ErrorCode.NO_PARLIAMENT_PROVIDED);
    }
}
