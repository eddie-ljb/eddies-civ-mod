package de.etiennebader.civ.domain.territory;

import java.util.Objects;
import java.util.Set;

/**
 * Koordinate eines Chunks (16 × 16 Blöcke) in einer Dimension.
 */
public record ChunkCoord(DimensionId dimension, int x, int z) {

    /** Kantenlänge eines Chunks in Blöcken. */
    public static final int SIZE = 16;

    public ChunkCoord {
        Objects.requireNonNull(dimension, "dimension");
    }

    /** Der Chunk, der den Block an {@code (blockX, blockZ)} enthält, auch für negative Koordinaten. */
    public static ChunkCoord containing(DimensionId dimension, int blockX, int blockZ) {
        return new ChunkCoord(dimension, Math.floorDiv(blockX, SIZE), Math.floorDiv(blockZ, SIZE));
    }

    /** Die vier direkt angrenzenden Chunks (Norden, Süden, Westen, Osten). */
    public Set<ChunkCoord> neighbours() {
        return Set.of(
                new ChunkCoord(dimension, x, z - 1),
                new ChunkCoord(dimension, x, z + 1),
                new ChunkCoord(dimension, x - 1, z),
                new ChunkCoord(dimension, x + 1, z));
    }

    /** Ob beide Chunks in derselben Dimension liegen und eine gemeinsame Kante haben. */
    public boolean isAdjacentTo(ChunkCoord other) {
        Objects.requireNonNull(other, "other");
        return dimension.equals(other.dimension)
                && Math.abs((long) x - other.x) + Math.abs((long) z - other.z) == 1;
    }

    /** Ein Block in der Mitte des Chunks auf der angegebenen Höhe. */
    public BlockPosition center(int y) {
        return new BlockPosition(dimension, minBlockX() + SIZE / 2, y, minBlockZ() + SIZE / 2);
    }

    public int minBlockX() {
        return x * SIZE;
    }

    public int minBlockZ() {
        return z * SIZE;
    }

    public int maxBlockX() {
        return minBlockX() + SIZE - 1;
    }

    public int maxBlockZ() {
        return minBlockZ() + SIZE - 1;
    }
}
