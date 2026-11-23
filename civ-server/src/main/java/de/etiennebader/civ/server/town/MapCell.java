package de.etiennebader.civ.server.town;

import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.server.territory.Relation;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Feld der Gebietskarte.
 *
 * @param here  ob der Spieler in diesem Chunk steht
 * @param group Kennung der Polity, deren Farbe dieses Feld zeigt (Bund der höchsten oder der gefilterten Ebene,
 *              sonst die Gemeinde selbst), leer in der Wildnis; siehe {@link de.etiennebader.civ.server.level.TerritoryColor}
 */
public record MapCell(ChunkCoord chunk, Relation relation, Optional<String> owner, Optional<ClaimType> type,
                      boolean here, Optional<String> group, Optional<String> port) {

    public MapCell {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(port, "port");
    }

    /** Ein Feld ohne Heimat-Hafen. */
    public MapCell(ChunkCoord chunk, Relation relation, Optional<String> owner, Optional<ClaimType> type,
                   boolean here, Optional<String> group) {
        this(chunk, relation, owner, type, here, group, Optional.empty());
    }
}
