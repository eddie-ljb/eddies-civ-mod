package de.etiennebader.civ.server.command;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;

import java.util.Objects;

/**
 * Der Spieler, der einen Befehl ausführt, und der Block, auf dem er gerade steht.
 */
public record Caller(PlayerRef player, String name, BlockPosition position) {

    public Caller {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
    }

    /** Der Chunk, in dem der Spieler steht. */
    public ChunkCoord chunk() {
        return position.chunk();
    }
}
