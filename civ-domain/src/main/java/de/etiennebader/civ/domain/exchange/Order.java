package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.OrderId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Limit-Order: kaufe oder verkaufe bis zu {@code quantity} Aktien zu höchstens bzw. mindestens {@code limit}
 * Einheiten je Aktie.
 *
 * @param number    laufende Nummer an der Börse, ab 1
 * @param remaining noch offene Stückzahl
 */
public record Order(OrderId id, int number, PolityId company, ActorRef trader, Side side, long quantity,
                    long remaining, long limit, Day placedOn) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(trader, "trader");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(placedOn, "placedOn");
        if (quantity <= 0 || remaining < 0 || remaining > quantity || limit <= 0) {
            throw new IllegalArgumentException("Ungültige Order: " + quantity + "/" + remaining + " zu " + limit);
        }
    }

    Order withRemaining(long rest) {
        return new Order(id, number, company, trader, side, quantity, rest, limit, placedOn);
    }

    Order numbered(int newNumber) {
        return new Order(id, newNumber, company, trader, side, quantity, remaining, limit, placedOn);
    }
}
