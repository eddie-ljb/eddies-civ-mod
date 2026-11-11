package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Für dieses Amt läuft schon eine Wahl.
 */
public final class ElectionAlreadyOpenException extends DomainException {

    public ElectionAlreadyOpenException(Office office) {
        super(ErrorCode.ELECTION_ALREADY_OPEN, office);
    }
}
