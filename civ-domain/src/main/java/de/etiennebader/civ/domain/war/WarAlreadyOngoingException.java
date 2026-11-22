package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

/** Zwischen diesen beiden Polities läuft schon ein Krieg. */
public final class WarAlreadyOngoingException extends DomainException {

    public WarAlreadyOngoingException(PolityId aggressor, PolityId defender) {
        super(ErrorCode.WAR_ALREADY_ONGOING, aggressor, defender);
    }
}
