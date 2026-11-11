package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das darf nur der Inhaber eines bestimmten Amts, meist der Bürgermeister.
 */
public final class NotOfficeHolderException extends DomainException {

    public NotOfficeHolderException(ActorRef actor, Office office) {
        super(ErrorCode.NOT_OFFICE_HOLDER, actor, office);
    }
}
