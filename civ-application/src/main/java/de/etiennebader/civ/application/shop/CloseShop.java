package de.etiennebader.civ.application.shop;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shop.ShopRegistry;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;
import java.util.Optional;

/**
 * Schließt einen Laden: der Inhaber selbst, oder ohne Prüfung ({@code actor} leer), wenn die Kiste fehlt.
 */
public final class CloseShop {

    public record Command(BlockPosition position, Optional<ActorRef> actor, Day day) {

        public Command {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public CloseShop(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        ShopRegistry shops = aggregates.shops();
        if (command.actor().isPresent()) {
            shops.close(command.position(), command.actor().get(), command.day());
        } else {
            shops.remove(command.position(), command.day());
        }
        aggregates.commit(shops);
    }
}
