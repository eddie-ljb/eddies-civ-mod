package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/**
 * Alle Events des {@link TerritoryRegistry}-Aggregats.
 */
public sealed interface TerritoryEvent extends DomainEvent permits ChunkClaimed, ChunkReleased {

    PolityId polity();

    ChunkCoord chunk();
}
