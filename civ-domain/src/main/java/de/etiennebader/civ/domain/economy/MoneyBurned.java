package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Geld wurde von einem Konto abgebucht und vernichtet.
 */
public record MoneyBurned(ActorRef from, Money amount, SupplyChange reason, Day occurredOn) implements LedgerEvent {

    public MoneyBurned {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
