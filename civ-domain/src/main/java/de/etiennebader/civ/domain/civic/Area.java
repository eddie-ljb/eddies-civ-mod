package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;

import java.util.Objects;

/**
 * Ein rechteckiges Gebiet in Blockkoordinaten (beide Ecken eingeschlossen), z. B. ein Teil eines Wahlkreises.
 */
public record Area(DimensionId dimension, int minX, int minZ, int maxX, int maxZ) {

    public Area {
        Objects.requireNonNull(dimension, "dimension");
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Ecken vertauscht: " + minX + "," + minZ + " / " + maxX + "," + maxZ);
        }
    }

    /** Das Rechteck zwischen zwei beliebigen Ecken. */
    public static Area between(BlockPosition first, BlockPosition second) {
        if (!first.dimension().equals(second.dimension())) {
            throw new IllegalArgumentException("Ecken in verschiedenen Dimensionen");
        }
        return new Area(first.dimension(), Math.min(first.x(), second.x()), Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()), Math.max(first.z(), second.z()));
    }

    public boolean contains(BlockPosition position) {
        return position.dimension().equals(dimension) && position.x() >= minX && position.x() <= maxX
                && position.z() >= minZ && position.z() <= maxZ;
    }

    public long blocks() {
        return (long) (maxX - minX + 1) * (maxZ - minZ + 1);
    }
}
