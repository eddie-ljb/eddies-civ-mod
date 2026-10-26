package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.chunk;
import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimLimitPolicyTest {

    private final PolityId nordhain = new TestActors().polityId();
    private final TerritoryRegistry registry = TerritoryRegistry.empty();
    private final ClaimLimitPolicy policy = new ClaimLimitPolicy(2, 1);

    @Test
    @DisplayName("Limit = base + perMember × Mitglieder")
    void computesLimit() {
        assertEquals(2, policy.limitFor(0));
        assertEquals(5, policy.limitFor(3));
    }

    @Test
    @DisplayName("Unter dem Limit ist der Claim erlaubt")
    void allowsClaimBelowLimit() {
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0), chunk(1, 0));

        assertEquals(Optional.empty(), policy.check(context(chunk(2, 0), 1), registry));
    }

    @Test
    @DisplayName("Limit erreicht → LIMIT_REACHED, Außenposten zählen mit")
    void rejectsClaimAtLimit() {
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0), chunk(1, 0));
        seed(registry, nordhain, ClaimType.OUTPOST, chunk(9, 9));

        assertEquals(Optional.of(ClaimRejection.LIMIT_REACHED), policy.check(context(chunk(2, 0), 1), registry));
    }

    private ClaimContext context(ChunkCoord target, int memberCount) {
        return new ClaimContext(nordhain, ClaimType.CORE, target, memberCount, 0);
    }
}
