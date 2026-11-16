package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur kandidiert bei keiner laufenden Wahl, z. B. für ein Wahlversprechen.
 */
public final class NotRunningException extends DomainException {

    public NotRunningException() {
        super(ErrorCode.NOT_RUNNING);
    }
}
