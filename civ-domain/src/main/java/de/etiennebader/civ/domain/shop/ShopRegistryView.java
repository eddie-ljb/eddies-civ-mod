package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.List;
import java.util.Optional;

/**
 * Lesender Zugriff auf alle Läden.
 */
public interface ShopRegistryView {

    Optional<Shop> shopAt(BlockPosition position);

    /** Alle Läden in Eröffnungsreihenfolge. */
    List<Shop> shops();

    default List<Shop> shopsOf(ActorRef owner) {
        return shops().stream().filter(shop -> shop.owner().equals(owner)).toList();
    }
}
