package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Den Bürgermeister bestimmt eine Wahl, keine Ernennung.
 */
public final class MayorNotAppointableException extends DomainException {

    public MayorNotAppointableException() {
        super(ErrorCode.MAYOR_NOT_APPOINTABLE);
    }
}
