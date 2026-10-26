package de.etiennebader.civ.domain.fiscal;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Eine offene Steuerschuld.
 */
public record TaxArrear(ActorRef debtor, Money amount) {

    public TaxArrear {
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Steuerschuld muss positiv sein: " + amount);
        }
    }
}
