package de.etiennebader.civ.domain.testsupport;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;

/**
 * Orte in der Oberwelt für Tests.
 */
public final class Places {

    private Places() {
    }

    /** Ein Block in der Mitte des Chunks auf Höhe 64, z. B. als Sitz eines Gemeindehauses. */
    public static BlockPosition seatIn(int chunkX, int chunkZ) {
        return new ChunkCoord(DimensionId.OVERWORLD, chunkX, chunkZ).center(64);
    }
}
