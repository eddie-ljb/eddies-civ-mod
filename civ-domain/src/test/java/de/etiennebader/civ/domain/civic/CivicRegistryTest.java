package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wahlkreise lassen sich frei zuschneiden (Issue #17 "Gerrymandering"): beliebige, auch nicht
 * zusammenhängende Gebiete und Gemeinden können zugeordnet und wieder entfernt werden, auch aus
 * bereits bestehenden Wahlkreisen.
 */
class CivicRegistryTest {

    private static final Day DAY = Day.of(1);

    private static Area areaAt(int x, int z) {
        return Area.between(
                new BlockPosition(DimensionId.OVERWORLD, x, 64, z),
                new BlockPosition(DimensionId.OVERWORLD, x + 15, 64, z + 15));
    }

    @Test
    void constituencyAcceptsArbitraryNonContiguousAreas() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        ConstituencyId id = new ConstituencyId(UUID.randomUUID());
        registry.define(id, polity, "Nord", DAY);

        // Zwei weit auseinanderliegende, nicht benachbarte Gebiete im selben Wahlkreis.
        registry.addArea(id, areaAt(0, 0), DAY);
        registry.addArea(id, areaAt(10_000, -5_000), DAY);

        Constituency updated = registry.constituencies().stream()
                .filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        assertEquals(2, updated.areas().size());
    }

    @Test
    void removeAreaTakesOutExactlyOneAreaAndKeepsTheRest() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        ConstituencyId id = new ConstituencyId(UUID.randomUUID());
        registry.define(id, polity, "Nord", DAY);
        Area kept = areaAt(0, 0);
        Area toRemove = areaAt(10_000, -5_000);
        registry.addArea(id, kept, DAY);
        registry.addArea(id, toRemove, DAY);

        Constituency updated = registry.removeArea(id, toRemove, DAY);

        assertEquals(List.of(kept), updated.areas());
    }

    @Test
    void removeAreaRejectsAnAreaThatIsNotPartOfTheConstituency() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        ConstituencyId id = new ConstituencyId(UUID.randomUUID());
        registry.define(id, polity, "Nord", DAY);
        registry.addArea(id, areaAt(0, 0), DAY);

        assertThrows(IllegalArgumentException.class, () -> registry.removeArea(id, areaAt(99, 99), DAY));
    }

    @Test
    void removeTownTakesOutExactlyOneTownAndKeepsTheRest() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        ConstituencyId id = new ConstituencyId(UUID.randomUUID());
        registry.define(id, polity, "Nord", DAY);
        PolityId keptTown = new PolityId(UUID.randomUUID());
        PolityId removedTown = new PolityId(UUID.randomUUID());
        registry.addTown(id, keptTown, DAY);
        registry.addTown(id, removedTown, DAY);

        Constituency updated = registry.removeTown(id, removedTown, DAY);

        assertTrue(updated.includes(keptTown));
        assertFalse(updated.includes(removedTown));
    }

    @Test
    void removeTownRejectsATownThatDoesNotBelongToTheConstituency() {
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        ConstituencyId id = new ConstituencyId(UUID.randomUUID());
        registry.define(id, polity, "Nord", DAY);

        assertThrows(IllegalArgumentException.class,
                () -> registry.removeTown(id, new PolityId(UUID.randomUUID()), DAY));
    }

    @Test
    void existingConstituencyCanBeRedrawnByAddingAndRemovingAreasRepeatedly() {
        // Das ist der eigentliche "Gerrymandering"-Fall: ein bestehender Wahlkreis wird nachträglich
        // umgezogen, statt nur bei der Anlage einmalig festgelegt zu werden.
        CivicRegistry registry = CivicRegistry.empty();
        PolityId polity = new PolityId(UUID.randomUUID());
        ConstituencyId id = new ConstituencyId(UUID.randomUUID());
        registry.define(id, polity, "Nord", DAY);
        Area original = areaAt(0, 0);
        registry.addArea(id, original, DAY);

        registry.removeArea(id, original, DAY);
        Area redrawn = areaAt(-10_000, 10_000);
        Constituency updated = registry.addArea(id, redrawn, DAY);

        assertEquals(List.of(redrawn), updated.areas());
    }
}
