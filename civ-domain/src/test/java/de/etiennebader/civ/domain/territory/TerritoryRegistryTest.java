package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.etiennebader.civ.domain.territory.DimensionId.OVERWORLD;
import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRegistryTest {

    private static final ChunkCoord ORIGIN = new ChunkCoord(OVERWORLD, 0, 0);

    private final TestActors actors = new TestActors();
    private final PolityId nordhain = actors.polityId();
    private final PolityId eichenfurt = actors.polityId();
    private final TerritoryRegistry registry = TerritoryRegistry.empty();

    @Test
    @DisplayName("Erster Claim: Hoheit bei der Polity, Event ChunkClaimed")
    void firstClaimGrantsSovereignty() {
        registry.claim(core(nordhain, ORIGIN), ClaimPolicy.ALLOW_ALL, Day.of(1));

        assertEquals(Optional.of(nordhain), registry.sovereignOf(ORIGIN));
        assertEquals(Optional.of(new Claim(nordhain, ClaimType.CORE, Day.of(1))), registry.claimAt(ORIGIN));
        assertEquals(Set.of(ORIGIN), registry.territoryOf(nordhain));
        assertEquals(1, registry.countOf(nordhain, ClaimType.CORE));
        assertEquals(List.of(new ChunkClaimed(nordhain, ORIGIN, ClaimType.CORE, Day.of(1))), registry.pullEvents());
    }

    @Test
    @DisplayName("Chunk einer anderen Polity claimen → CHUNK_ALREADY_CLAIMED, auch ohne Policy")
    void rejectsChunkClaimedByOtherPolity() {
        registry.claim(core(nordhain, ORIGIN), ClaimPolicy.ALLOW_ALL, Day.of(1));

        var exception = assertDomainError(ErrorCode.CHUNK_ALREADY_CLAIMED,
                () -> registry.claim(core(eichenfurt, ORIGIN), ClaimPolicy.ALLOW_ALL, Day.of(2)));

        assertEquals(ClaimRejection.ALREADY_CLAIMED, assertInstanceOf(ClaimRejectedException.class, exception).rejection());
        assertEquals(Optional.of(nordhain), registry.sovereignOf(ORIGIN));
    }

    @Test
    @DisplayName("Ablehnung der Policy wird mit ihrem Fehlercode gemeldet")
    void reportsPolicyRejection() {
        ClaimPolicy denyAll = (context, territory) -> Optional.of(ClaimRejection.LIMIT_REACHED);

        assertDomainError(ErrorCode.CLAIM_LIMIT_REACHED, () -> registry.claim(core(nordhain, ORIGIN), denyAll, Day.of(1)));
        assertTrue(registry.territoryOf(nordhain).isEmpty());
        assertTrue(registry.pullEvents().isEmpty());
    }

    @Test
    @DisplayName("Freigabe entfernt die Hoheit, Event ChunkReleased")
    void releaseRemovesSovereignty() {
        registry.claim(core(nordhain, ORIGIN), ClaimPolicy.ALLOW_ALL, Day.of(1));
        registry.pullEvents();

        registry.release(nordhain, ORIGIN, Day.of(2));

        assertEquals(Optional.empty(), registry.sovereignOf(ORIGIN));
        assertEquals(List.of(new ChunkReleased(nordhain, ORIGIN, Day.of(2))), registry.pullEvents());
    }

    @Test
    @DisplayName("Fremden oder freien Chunk freigeben → NOT_SOVEREIGN")
    void rejectsReleaseOfForeignChunk() {
        registry.claim(core(nordhain, ORIGIN), ClaimPolicy.ALLOW_ALL, Day.of(1));

        assertDomainError(ErrorCode.NOT_SOVEREIGN, () -> registry.release(eichenfurt, ORIGIN, Day.of(2)));
        assertDomainError(ErrorCode.NOT_SOVEREIGN,
                () -> registry.release(nordhain, new ChunkCoord(OVERWORLD, 5, 5), Day.of(2)));
    }

    private static ClaimContext core(PolityId claimant, ChunkCoord target) {
        return new ClaimContext(claimant, ClaimType.CORE, target, 1, 0);
    }
}
