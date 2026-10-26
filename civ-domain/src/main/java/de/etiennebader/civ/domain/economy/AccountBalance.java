package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Kontostand eines Akteurs in einer Währung, z. B. zum Speichern und Wiederherstellen.
 */
public record AccountBalance(ActorRef account, Money balance) {

    public AccountBalance {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(balance, "balance");
        if (balance.isNegative()) {
            throw new IllegalArgumentException("Kontostand darf nicht negativ sein: " + account + " " + balance);
        }
    }
}
