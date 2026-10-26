package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Rechteck in Blockkoordinaten einer Dimension. Beide Grenzen sind inklusive,
 * ein Rechteck von {@code (0, 0)} bis {@code (9, 9)} umfasst also 10 × 10 Blöcke.
 */
public record Area(DimensionId dimension, int minX, int minZ, int maxX, int maxZ) {

    public Area {
        Objects.requireNonNull(dimension, "dimension");
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "Untere Grenze liegt über der oberen: (%d, %d) bis (%d, %d)".formatted(minX, minZ, maxX, maxZ));
        }
    }

    /** Rechteck zwischen zwei beliebigen Eckpunkten, z. B. aus einer Spielerauswahl. */
    public static Area spanning(DimensionId dimension, int firstX, int firstZ, int secondX, int secondZ) {
        return new Area(dimension,
                Math.min(firstX, secondX), Math.min(firstZ, secondZ),
                Math.max(firstX, secondX), Math.max(firstZ, secondZ));
    }

    /** Die Fläche eines ganzen Chunks. */
    public static Area ofChunk(ChunkCoord chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return new Area(chunk.dimension(), chunk.minBlockX(), chunk.minBlockZ(), chunk.maxBlockX(), chunk.maxBlockZ());
    }

    public long width() {
        return (long) maxX - minX + 1;
    }

    public long depth() {
        return (long) maxZ - minZ + 1;
    }

    /** Fläche in Blöcken. */
    public long size() {
        return width() * depth();
    }

    public boolean contains(DimensionId dimension, int x, int z) {
        return this.dimension.equals(dimension) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Ob beide Rechtecke mindestens einen gemeinsamen Block haben. */
    public boolean overlaps(Area other) {
        Objects.requireNonNull(other, "other");
        return dimension.equals(other.dimension)
                && minX <= other.maxX && other.minX <= maxX
                && minZ <= other.maxZ && other.minZ <= maxZ;
    }

    /** Ob das Rechteck den Chunk ganz oder teilweise berührt. */
    public boolean touches(ChunkCoord chunk) {
        return overlaps(ofChunk(chunk));
    }

    /** Alle Chunks, die das Rechteck ganz oder teilweise berührt, zeilenweise sortiert. */
    public Set<ChunkCoord> chunks() {
        ChunkCoord first = ChunkCoord.containing(dimension, minX, minZ);
        ChunkCoord last = ChunkCoord.containing(dimension, maxX, maxZ);
        Set<ChunkCoord> chunks = new LinkedHashSet<>();
        for (int chunkZ = first.z(); chunkZ <= last.z(); chunkZ++) {
            for (int chunkX = first.x(); chunkX <= last.x(); chunkX++) {
                chunks.add(new ChunkCoord(dimension, chunkX, chunkZ));
            }
        }
        return Collections.unmodifiableSet(chunks);
    }
}
