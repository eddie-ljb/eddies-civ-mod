package de.etiennebader.civ.server.town;

import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.server.territory.Relation;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Feld der Gebietskarte.
 *
 * @param here ob der Spieler in diesem Chunk steht
 */
public record MapCell(ChunkCoord chunk, Relation relation, Optional<String> owner, Optional<ClaimType> type,
                      boolean here) {

    public MapCell {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
    }
}
