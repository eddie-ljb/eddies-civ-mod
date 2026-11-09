package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Alle Läden der Welt, je Kistenposition höchstens einer.
 */
public final class ShopRegistry extends AggregateRoot<ShopEvent> implements ShopRegistryView {

    private final Map<BlockPosition, Shop> shops = new LinkedHashMap<>();

    private ShopRegistry() {
    }

    public static ShopRegistry empty() {
        return new ShopRegistry();
    }

    public static ShopRegistry restore(Collection<Shop> stored) {
        ShopRegistry registry = new ShopRegistry();
        stored.forEach(shop -> registry.shops.put(shop.position(), shop));
        return registry;
    }

    /**
     * Eröffnet einen Laden oder ändert Ware und Preis eines eigenen.
     *
     * @throws NotShopOwnerException wenn an der Stelle schon der Laden eines anderen steht
     */
    public Shop open(BlockPosition position, ActorRef owner, Commodity commodity, Money price, Day day) {
        Shop existing = shops.get(Objects.requireNonNull(position, "position"));
        if (existing != null && !existing.owner().equals(owner)) {
            throw new NotShopOwnerException(owner);
        }
        Shop shop = new Shop(position, owner, commodity, price,
                existing == null ? day : existing.openedOn());
        shops.put(position, shop);
        raise(new ShopOpened(position, owner, commodity, price, day));
        return shop;
    }

    /**
     * Schließt einen Laden.
     *
     * @throws UnknownShopException  wenn dort kein Laden ist
     * @throws NotShopOwnerException wenn {@code actor} nicht der Inhaber ist
     */
    public void close(BlockPosition position, ActorRef actor, Day day) {
        Shop shop = require(position);
        if (!shop.owner().equals(actor)) {
            throw new NotShopOwnerException(actor);
        }
        remove(position, day);
    }

    /** Entfernt einen Laden ohne Prüfung des Inhabers, z. B. weil die Kiste verschwunden ist. */
    public void remove(BlockPosition position, Day day) {
        if (shops.remove(Objects.requireNonNull(position, "position")) != null) {
            raise(new ShopClosed(position, day));
        }
    }

    /** Hält einen Kauf fest; das Geld bewegt der Aufrufer. */
    public void recordSale(BlockPosition position, ActorRef buyer, int quantity, Money total, Money tax, Day day) {
        require(position);
        raise(new ShopSale(position, buyer, quantity, total, tax, day));
    }

    @Override
    public Optional<Shop> shopAt(BlockPosition position) {
        return Optional.ofNullable(shops.get(position));
    }

    @Override
    public List<Shop> shops() {
        return List.copyOf(shops.values());
    }

    private Shop require(BlockPosition position) {
        Shop shop = shops.get(Objects.requireNonNull(position, "position"));
        if (shop == null) {
            throw new UnknownShopException(position);
        }
        return shop;
    }
}
