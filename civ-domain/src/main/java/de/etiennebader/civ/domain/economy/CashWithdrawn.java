package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Buchgeld wurde als Bargeld ausgezahlt.
 */
public record CashWithdrawn(ActorRef account, Money amount, Day occurredOn) implements LedgerEvent {

    public CashWithdrawn {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
