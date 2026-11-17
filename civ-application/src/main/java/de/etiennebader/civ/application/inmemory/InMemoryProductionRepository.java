package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.production.ProductionRegistry;
import de.etiennebader.civ.domain.production.ProductionRepository;

import java.util.Objects;

/** Firmenbetriebe im Speicher. */
public final class InMemoryProductionRepository implements ProductionRepository {

    private ProductionRegistry registry = ProductionRegistry.empty();

    @Override
    public ProductionRegistry load() {
        return registry;
    }

    @Override
    public void save(ProductionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }
}
