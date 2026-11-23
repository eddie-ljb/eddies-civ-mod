package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.economy.CurrencyRegistry;
import de.etiennebader.civ.domain.economy.CurrencyRepository;

import java.util.Objects;

/** Währungen im Speicher. */
public final class InMemoryCurrencyRepository implements CurrencyRepository {

    private CurrencyRegistry registry = CurrencyRegistry.empty();

    @Override
    public CurrencyRegistry load() {
        return registry;
    }

    @Override
    public void save(CurrencyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }
}
