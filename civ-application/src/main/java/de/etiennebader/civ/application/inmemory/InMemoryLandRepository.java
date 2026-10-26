package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.LandRepository;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Hält die Grundbücher aller Polities im Speicher.
 */
public final class InMemoryLandRepository implements LandRepository {

    private final Map<PolityId, LandRegistry> registries = new LinkedHashMap<>();

    @Override
    public Optional<LandRegistry> findByPolity(PolityId polity) {
        return Optional.ofNullable(registries.get(Objects.requireNonNull(polity, "polity")));
    }

    @Override
    public void save(LandRegistry registry) {
        registries.put(registry.owner(), registry);
    }
}
