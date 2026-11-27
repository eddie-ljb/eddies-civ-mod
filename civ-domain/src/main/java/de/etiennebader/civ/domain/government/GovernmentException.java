package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Verstoß gegen eine Regel zu Ministerien, Haushalt oder Oberhaupt. */
public final class GovernmentException extends DomainException {

    public GovernmentException(ErrorCode code, Object... arguments) {
        super(code, arguments);
    }
}
