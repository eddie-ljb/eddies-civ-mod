package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.chunk;
import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdjacencyClaimPolicyTest {

    private final TestActors actors = new TestActors();
    private final PolityId nordhain = actors.polityId();
    private final PolityId eichenfurt = actors.polityId();
    private final TerritoryRegistry registry = TerritoryRegistry.empty();
    private final AdjacencyClaimPolicy policy = new AdjacencyClaimPolicy();

    @Test
    @DisplayName("Erster CORE-Claim ist frei")
    void firstCoreClaimIsAllowed() {
        assertEquals(Optional.empty(), policy.check(context(ClaimType.CORE, chunk(10, 10)), registry));
    }

    @Test
    @DisplayName("Angrenzender CORE-Claim ist erlaubt")
    void adjacentCoreClaimIsAllowed() {
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0));

        assertEquals(Optional.empty(), policy.check(context(ClaimType.CORE, chunk(1, 0)), registry));
    }

    @Test
    @DisplayName("Nicht angrenzender CORE-Claim → NOT_ADJACENT")
    void detachedCoreClaimIsRejected() {
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0));

        assertEquals(Optional.of(ClaimRejection.NOT_ADJACENT), policy.check(context(ClaimType.CORE, chunk(2, 0)), registry));
        assertEquals(Optional.of(ClaimRejection.NOT_ADJACENT), policy.check(context(ClaimType.CORE, chunk(1, 1)), registry));
    }

    @Test
    @DisplayName("Angrenzen an fremdes Gebiet oder eigenen Außenposten genügt nicht")
    void foreignTerritoryAndOutpostsDoNotCount() {
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0));
        seed(registry, nordhain, ClaimType.OUTPOST, chunk(10, 0));
        seed(registry, eichenfurt, ClaimType.CORE, chunk(20, 0));

        assertEquals(Optional.of(ClaimRejection.NOT_ADJACENT), policy.check(context(ClaimType.CORE, chunk(11, 0)), registry));
        assertEquals(Optional.of(ClaimRejection.NOT_ADJACENT), policy.check(context(ClaimType.CORE, chunk(21, 0)), registry));
    }

    @Test
    @DisplayName("Nicht angrenzender OUTPOST-Claim ist erlaubt")
    void detachedOutpostIsAllowed() {
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0));

        assertEquals(Optional.empty(), policy.check(context(ClaimType.OUTPOST, chunk(50, 50)), registry));
    }

    private ClaimContext context(ClaimType type, ChunkCoord target) {
        return new ClaimContext(nordhain, type, target, 1, 5);
    }
}
