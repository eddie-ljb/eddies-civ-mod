package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Es gibt bereits eine Polity mit diesem Namen, unabhängig von Groß- und Kleinschreibung.
 */
public final class PolityNameTakenException extends DomainException {

    public PolityNameTakenException(PolityName name) {
        super(ErrorCode.POLITY_NAME_TAKEN, name.value());
    }
}
