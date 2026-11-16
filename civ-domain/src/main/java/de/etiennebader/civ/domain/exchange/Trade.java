package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein ausgeführtes Geschäft.
 *
 * @param price Einheiten je Aktie
 */
public record Trade(PolityId company, ActorRef buyer, ActorRef seller, long quantity, long price, Day day) {

    public Trade {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(day, "day");
        if (quantity <= 0 || price <= 0) {
            throw new IllegalArgumentException("Ungültiges Geschäft");
        }
    }

    public long value() {
        return quantity * price;
    }
}
