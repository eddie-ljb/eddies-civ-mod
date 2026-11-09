package de.etiennebader.civ.domain.shop;

/**
 * Speicher für die Läden; es gibt genau eine {@link ShopRegistry}.
 */
public interface ShopRegistryRepository {

    ShopRegistry load();

    void save(ShopRegistry shops);
}
