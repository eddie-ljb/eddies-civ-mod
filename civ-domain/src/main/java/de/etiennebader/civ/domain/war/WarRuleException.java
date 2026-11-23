package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Verstoß gegen eine Regel von Feldzug, Bündnis, Anspruch oder Truppenaufbau. */
public final class WarRuleException extends DomainException {

    public WarRuleException(ErrorCode code, Object... arguments) {
        super(code, arguments);
    }
}
