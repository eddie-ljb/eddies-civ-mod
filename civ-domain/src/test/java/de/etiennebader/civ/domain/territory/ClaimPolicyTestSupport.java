package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

/**
 * Gemeinsame Hilfen für Policy-Tests: ein Register, in das ohne Regeln geclaimt wird.
 */
final class ClaimPolicyTestSupport {

    private ClaimPolicyTestSupport() {
    }

    static ChunkCoord chunk(int x, int z) {
        return new ChunkCoord(DimensionId.OVERWORLD, x, z);
    }

    static void seed(TerritoryRegistry registry, PolityId polity, ClaimType type, ChunkCoord... chunks) {
        for (ChunkCoord chunk : chunks) {
            registry.claim(new ClaimContext(polity, type, chunk, 1, Integer.MAX_VALUE), ClaimPolicy.ALLOW_ALL, Day.ZERO);
        }
    }
}
