package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Polity hat die Hoheit über einen Chunk aufgegeben.
 */
public record ChunkReleased(PolityId polity, ChunkCoord chunk, Day occurredOn) implements TerritoryEvent {

    public ChunkReleased {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
