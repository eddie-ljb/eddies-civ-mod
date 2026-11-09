package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Jemand hat in einem Laden gekauft.
 *
 * @param total gezahlter Gesamtpreis einschließlich Steuer
 * @param tax   davon an die Gemeinde
 */
public record ShopSale(BlockPosition position, ActorRef buyer, int quantity, Money total, Money tax, Day occurredOn)
        implements ShopEvent {

    public ShopSale {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(tax, "tax");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
