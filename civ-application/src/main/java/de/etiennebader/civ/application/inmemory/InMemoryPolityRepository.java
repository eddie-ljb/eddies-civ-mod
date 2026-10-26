package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityRepository;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Hält Polities im Speicher; {@link #findAll()} liefert sie in Gründungsreihenfolge.
 */
public final class InMemoryPolityRepository implements PolityRepository {

    private final Map<PolityId, Polity> polities = new LinkedHashMap<>();

    @Override
    public Optional<Polity> findById(PolityId id) {
        return Optional.ofNullable(polities.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public Collection<Polity> findAll() {
        return List.copyOf(polities.values());
    }

    @Override
    public void save(Polity polity) {
        polities.put(polity.id(), polity);
    }
}
