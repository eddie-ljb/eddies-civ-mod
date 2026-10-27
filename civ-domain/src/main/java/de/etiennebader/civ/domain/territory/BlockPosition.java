package de.etiennebader.civ.domain.territory;

import java.util.Objects;

/**
 * Ein einzelner Block in einer Dimension, z. B. der Standort eines Gemeindehauses.
 */
public record BlockPosition(DimensionId dimension, int x, int y, int z) {

    public BlockPosition {
        Objects.requireNonNull(dimension, "dimension");
    }

    /** Der Chunk, in dem der Block liegt. */
    public ChunkCoord chunk() {
        return ChunkCoord.containing(dimension, x, z);
    }

    @Override
    public String toString() {
        return dimension + " " + x + " " + y + " " + z;
    }
}
