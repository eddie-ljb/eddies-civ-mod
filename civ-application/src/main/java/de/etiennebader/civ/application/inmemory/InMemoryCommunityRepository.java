package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.community.Community;
import de.etiennebader.civ.domain.community.CommunityRepository;

import java.util.Objects;

/**
 * Hält Briefe, Freundschaften und Chat im Arbeitsspeicher.
 */
public final class InMemoryCommunityRepository implements CommunityRepository {

    private Community value = Community.empty();

    @Override
    public Community load() {
        return value;
    }

    @Override
    public void save(Community value) {
        this.value = Objects.requireNonNull(value, "value");
    }
}
