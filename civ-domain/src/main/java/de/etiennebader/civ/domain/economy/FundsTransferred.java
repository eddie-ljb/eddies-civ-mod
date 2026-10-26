package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Buchgeld wurde von einem Konto auf ein anderes überwiesen.
 */
public record FundsTransferred(ActorRef from, ActorRef to, Money amount, TransferReason reason, Day occurredOn) implements LedgerEvent {

    public FundsTransferred {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
