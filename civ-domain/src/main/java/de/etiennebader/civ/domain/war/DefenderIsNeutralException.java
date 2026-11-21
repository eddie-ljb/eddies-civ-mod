package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;

/** Die Verteidigerin hat sich Neutralität erkauft und kann nicht angegriffen werden. */
public final class DefenderIsNeutralException extends DomainException {

    public DefenderIsNeutralException(PolityId defender) {
        super(ErrorCode.DEFENDER_IS_NEUTRAL, defender);
    }
}
