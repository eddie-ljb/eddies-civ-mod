package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Geldmenge und umlaufendes Bargeld einer Währung, z. B. zum Speichern und Wiederherstellen.
 */
public record CurrencyTotals(CurrencyId currency, Money supply, Money cashInCirculation) {

    public CurrencyTotals {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(supply, "supply");
        Objects.requireNonNull(cashInCirculation, "cashInCirculation");
        if (!supply.currency().equals(currency) || !cashInCirculation.currency().equals(currency)) {
            throw new IllegalArgumentException("Beträge passen nicht zur Währung " + currency);
        }
        if (supply.isNegative() || cashInCirculation.isNegative()) {
            throw new IllegalArgumentException("Geldmenge und Bargeld dürfen nicht negativ sein: " + currency);
        }
    }
}
