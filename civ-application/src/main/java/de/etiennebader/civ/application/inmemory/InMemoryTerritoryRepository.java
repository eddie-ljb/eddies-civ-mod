package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.domain.territory.TerritoryRepository;

import java.util.Objects;

/**
 * Hält das weltweite Hoheitsregister im Speicher.
 */
public final class InMemoryTerritoryRepository implements TerritoryRepository {

    private TerritoryRegistry registry = TerritoryRegistry.empty();

    @Override
    public TerritoryRegistry load() {
        return registry;
    }

    @Override
    public void save(TerritoryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }
}
