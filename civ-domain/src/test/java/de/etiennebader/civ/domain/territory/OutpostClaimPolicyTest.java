package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.chunk;
import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OutpostClaimPolicyTest {

    private final PolityId nordhain = new TestActors().polityId();
    private final TerritoryRegistry registry = TerritoryRegistry.empty();
    private final OutpostClaimPolicy policy = new OutpostClaimPolicy();

    @Test
    @DisplayName("Außenposten unter dem Limit sind erlaubt")
    void allowsOutpostBelowLimit() {
        seed(registry, nordhain, ClaimType.OUTPOST, chunk(10, 10));

        assertEquals(Optional.empty(), policy.check(outpost(chunk(20, 20), 2), registry));
    }

    @Test
    @DisplayName("Mehr Außenposten als maxOutposts → OUTPOST_LIMIT_REACHED")
    void rejectsOutpostAtLimit() {
        seed(registry, nordhain, ClaimType.OUTPOST, chunk(10, 10), chunk(30, 30));

        assertEquals(Optional.of(ClaimRejection.OUTPOST_LIMIT_REACHED), policy.check(outpost(chunk(20, 20), 2), registry));
    }

    @Test
    @DisplayName("Standardrecht (0 Außenposten) verbietet Außenposten")
    void defaultLawForbidsOutposts() {
        assertEquals(Optional.of(ClaimRejection.OUTPOST_LIMIT_REACHED), policy.check(outpost(chunk(0, 0), 0), registry));
    }

    @Test
    @DisplayName("CORE-Claims sind nicht betroffen")
    void ignoresCoreClaims() {
        ClaimContext core = new ClaimContext(nordhain, ClaimType.CORE, chunk(0, 0), 1, 0);

        assertEquals(Optional.empty(), policy.check(core, registry));
    }

    private ClaimContext outpost(ChunkCoord target, int maxOutposts) {
        return new ClaimContext(nordhain, ClaimType.OUTPOST, target, 1, maxOutposts);
    }
}
