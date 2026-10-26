package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;

import java.util.Objects;

/**
 * Ein Konto: Akteur und Währung.
 */
public record AccountKey(ActorRef account, CurrencyId currency) {

    public AccountKey {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(currency, "currency");
    }
}
