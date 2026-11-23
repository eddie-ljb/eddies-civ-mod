package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Nur der Zentralbank-Vorsitzende (vor der ersten Ernennung: die Gründerin bzw. der Gründer) darf das. */
public final class NotCurrencyGovernorException extends DomainException {

    public NotCurrencyGovernorException(CurrencyId currency, ActorRef actor) {
        super(ErrorCode.NOT_CURRENCY_GOVERNOR, currency, actor);
    }
}
