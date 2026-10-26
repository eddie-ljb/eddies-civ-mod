package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Optional;

/**
 * Port zur Speicherung der Grundbücher.
 */
public interface LandRepository {

    Optional<LandRegistry> findByPolity(PolityId polity);

    void save(LandRegistry registry);
}
