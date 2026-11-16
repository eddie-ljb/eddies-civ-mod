package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Mit gewähltem Parlament braucht jede Rechtsänderung seine Zustimmung; verordnen geht nicht.
 */
public final class ParliamentDecidesException extends DomainException {

    public ParliamentDecidesException() {
        super(ErrorCode.PARLIAMENT_DECIDES);
    }
}
