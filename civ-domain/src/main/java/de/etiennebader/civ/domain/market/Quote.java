package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Aktuelle Preise einer Ware für ein Stück.
 *
 * @param sellPrice was der Markt einem Verkäufer zahlt
 * @param buyPrice  was ein Käufer zahlt; nur sinnvoll, wenn {@code stock > 0}
 * @param stock     heutiger Lagerbestand
 */
public record Quote(Commodity commodity, Money sellPrice, Money buyPrice, long stock) {

    public Quote {
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(sellPrice, "sellPrice");
        Objects.requireNonNull(buyPrice, "buyPrice");
    }

    public boolean available() {
        return stock > 0;
    }
}
