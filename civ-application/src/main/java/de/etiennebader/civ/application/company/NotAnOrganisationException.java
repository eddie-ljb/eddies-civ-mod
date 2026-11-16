package de.etiennebader.civ.application.company;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Die Polity ist nicht die erwartete Firma oder Zeitung. */
public final class NotAnOrganisationException extends DomainException {

    public NotAnOrganisationException(Object name) {
        super(ErrorCode.NOT_AN_ORGANISATION, name);
    }
}
