package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Dorfbewohner nimmt die Stelle nicht an: Er arbeitet schon, oder der Lohn ist ihm zu niedrig.
 */
public final class VillagerDeclinesException extends DomainException {

    public VillagerDeclinesException(long minimumWage) {
        super(ErrorCode.VILLAGER_DECLINES, minimumWage);
    }
}
