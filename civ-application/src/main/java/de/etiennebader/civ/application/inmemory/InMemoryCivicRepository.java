package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.civic.CivicRegistry;
import de.etiennebader.civ.domain.civic.CivicRepository;

import java.util.Objects;

/** Wahlkreise und Hoheitszeichen im Speicher. */
public final class InMemoryCivicRepository implements CivicRepository {

    private CivicRegistry registry = CivicRegistry.empty();

    @Override
    public CivicRegistry load() {
        return registry;
    }

    @Override
    public void save(CivicRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }
}
