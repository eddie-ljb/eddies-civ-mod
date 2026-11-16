package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Verfassung ist bereits ausgerufen; Ausrufen und Präambel gehen nur beim Entwurf.
 */
public final class ConstitutionAlreadyProclaimedException extends DomainException {

    public ConstitutionAlreadyProclaimedException(PolityName polity) {
        super(ErrorCode.CONSTITUTION_ALREADY_PROCLAIMED, polity.value());
    }
}
