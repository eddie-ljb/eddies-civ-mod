package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Polity hat die Hoheit über einen Chunk übernommen.
 */
public record ChunkClaimed(PolityId polity, ChunkCoord chunk, ClaimType type, Day occurredOn) implements TerritoryEvent {

    public ChunkClaimed {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
