package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Geld wurde geschöpft und gutgeschrieben.
 */
public record MoneyMinted(ActorRef to, Money amount, SupplyChange reason, Day occurredOn) implements LedgerEvent {

    public MoneyMinted {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
