package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.shop.ShopRegistry;
import de.etiennebader.civ.domain.shop.ShopRegistryRepository;

import java.util.Objects;

/**
 * Hält die Läden im Arbeitsspeicher.
 */
public final class InMemoryShopRepository implements ShopRegistryRepository {

    private ShopRegistry shops = ShopRegistry.empty();

    @Override
    public ShopRegistry load() {
        return shops;
    }

    @Override
    public void save(ShopRegistry shops) {
        this.shops = Objects.requireNonNull(shops, "shops");
    }
}
