package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.chunk;
import static de.etiennebader.civ.domain.territory.ClaimPolicyTestSupport.seed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeClaimPolicyTest {

    private final PolityId nordhain = new TestActors().polityId();
    private final TerritoryRegistry registry = TerritoryRegistry.empty();

    @Test
    @DisplayName("Erste Ablehnung gewinnt")
    void firstRejectionWins() {
        CompositeClaimPolicy policy = CompositeClaimPolicy.of(
                ClaimPolicy.ALLOW_ALL,
                (context, territory) -> Optional.of(ClaimRejection.NOT_ADJACENT),
                (context, territory) -> Optional.of(ClaimRejection.LIMIT_REACHED));

        assertEquals(Optional.of(ClaimRejection.NOT_ADJACENT), policy.check(context(chunk(0, 0)), registry));
    }

    @Test
    @DisplayName("Ohne Policies ist alles erlaubt")
    void emptyCompositeAllowsAll() {
        assertEquals(Optional.empty(), CompositeClaimPolicy.of().check(context(chunk(0, 0)), registry));
    }

    @Test
    @DisplayName("Standardregeln: zusammenhängend und begrenzt")
    void standardPolicyCombinesRules() {
        CompositeClaimPolicy standard = CompositeClaimPolicy.standard(2, 0);
        seed(registry, nordhain, ClaimType.CORE, chunk(0, 0));

        assertEquals(Optional.of(ClaimRejection.NOT_ADJACENT), standard.check(context(chunk(5, 5)), registry));
        assertEquals(Optional.empty(), standard.check(context(chunk(1, 0)), registry));

        seed(registry, nordhain, ClaimType.CORE, chunk(1, 0));
        assertEquals(Optional.of(ClaimRejection.LIMIT_REACHED), standard.check(context(chunk(2, 0)), registry));
    }

    private ClaimContext context(ChunkCoord target) {
        return new ClaimContext(nordhain, ClaimType.CORE, target, 1, 0);
    }
}
