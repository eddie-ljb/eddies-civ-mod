package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collection;
import java.util.Optional;

/**
 * Port zur Speicherung von Polities. Implementierungen liegen außerhalb des Kerns.
 */
public interface PolityRepository {

    Optional<Polity> findById(PolityId id);

    Collection<Polity> findAll();

    void save(Polity polity);
}
