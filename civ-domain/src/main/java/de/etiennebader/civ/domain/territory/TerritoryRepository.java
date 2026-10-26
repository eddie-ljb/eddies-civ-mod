package de.etiennebader.civ.domain.territory;

/**
 * Port zur Speicherung des weltweiten Hoheitsregisters.
 */
public interface TerritoryRepository {

    TerritoryRegistry load();

    void save(TerritoryRegistry registry);
}
