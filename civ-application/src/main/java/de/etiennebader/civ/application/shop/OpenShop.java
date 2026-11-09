package de.etiennebader.civ.application.shop;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shop.Shop;
import de.etiennebader.civ.domain.shop.ShopRegistry;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Eröffnet einen Laden an einer Kiste oder ändert Ware und Preis. Ob der Spieler dort bauen darf, prüft der Aufrufer.
 */
public final class OpenShop {

    public record Command(BlockPosition position, ActorRef owner, Commodity commodity, Money price, Day day) {

        public Command {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(commodity, "commodity");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public OpenShop(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public Shop execute(Command command) {
        ShopRegistry shops = aggregates.shops();
        Shop shop = shops.open(command.position(), command.owner(), command.commodity(), command.price(), command.day());
        aggregates.commit(shops);
        return shop;
    }
}
