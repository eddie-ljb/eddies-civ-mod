package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flaggen und Wappen als Blöcke (Issue #82): Treue-Bonus, Obergrenze und Ersetzen am selben Ort. */
class EmblemTest {

    private static final Day DAY = Day.of(1);
    private final PolityId town = new PolityId(UUID.randomUUID());

    private static BlockPosition at(int x) {
        return new BlockPosition(DimensionId.OVERWORLD, x, 64, 0);
    }

    @Test
    void flagsAndArmsRaiseLoyaltyUpToTheCap() {
        CivicRegistry registry = CivicRegistry.empty();
        assertEquals(0, registry.emblemLoyalty(town));
        registry.placeEmblem(new Emblem(at(0), town, Emblem.Kind.FLAG, DAY));
        registry.placeEmblem(new Emblem(at(1), town, Emblem.Kind.COAT_OF_ARMS, DAY));
        assertEquals(Emblem.FLAG_LOYALTY + Emblem.ARMS_LOYALTY, registry.emblemLoyalty(town));
        for (int i = 2; i < 20; i++) {
            registry.placeEmblem(new Emblem(at(i), town, Emblem.Kind.COAT_OF_ARMS, DAY));
        }
        assertEquals(Emblem.LOYALTY_CAP, registry.emblemLoyalty(town));
    }

    @Test
    void placingAtTheSamePositionReplacesAndTheLimitHolds() {
        CivicRegistry registry = CivicRegistry.empty();
        assertTrue(registry.placeEmblem(new Emblem(at(0), town, Emblem.Kind.FLAG, DAY)));
        assertTrue(registry.placeEmblem(new Emblem(at(0), town, Emblem.Kind.COAT_OF_ARMS, DAY)));
        assertEquals(1, registry.emblemsOf(town).size());
        for (int i = 1; i < Emblem.MAX_PER_POLITY; i++) {
            assertTrue(registry.placeEmblem(new Emblem(at(i), town, Emblem.Kind.FLAG, DAY)));
        }
        assertFalse(registry.placeEmblem(new Emblem(at(100), town, Emblem.Kind.FLAG, DAY)));
        assertTrue(registry.placeEmblem(new Emblem(at(3), town, Emblem.Kind.COAT_OF_ARMS, DAY)));
    }

    @Test
    void removingStopsTheBonus() {
        CivicRegistry registry = CivicRegistry.empty();
        registry.placeEmblem(new Emblem(at(0), town, Emblem.Kind.FLAG, DAY));
        assertTrue(registry.removeEmblem(at(0)));
        assertFalse(registry.removeEmblem(at(0)));
        assertEquals(0, registry.emblemLoyalty(town));
    }
}
