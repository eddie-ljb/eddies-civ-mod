package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.credit.CreditRegistry;
import de.etiennebader.civ.domain.credit.CreditRepository;

import java.util.Objects;

/** Kredite im Speicher. */
public final class InMemoryCreditRepository implements CreditRepository {

    private CreditRegistry registry = CreditRegistry.empty();

    @Override
    public CreditRegistry load() {
        return registry;
    }

    @Override
    public void save(CreditRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }
}
