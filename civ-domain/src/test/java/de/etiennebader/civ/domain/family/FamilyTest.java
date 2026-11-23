package de.etiennebader.civ.domain.family;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTest {

    private static PersonRef person(long n) {
        return new PersonRef(new PersonId(new UUID(n * 7919, n * 104_729)));
    }

    private final PersonRef anna = person(1);
    private final PersonRef ben = person(2);

    @Test
    @DisplayName("Verträglichkeit ist symmetrisch, begrenzt und mit sich selbst am höchsten")
    void compatibility() {
        assertEquals(Family.compatibility(anna, ben), Family.compatibility(ben, anna));
        assertEquals(100, Family.compatibility(anna, anna));
        for (long n = 1; n < 50; n++) {
            int value = Family.compatibility(anna, person(n));
            assertTrue(value >= 0 && value <= 100);
        }
    }

    @Test
    @DisplayName("Bildung ist stabil und wächst mit dem Aufenthalt, höchstens bis 100")
    void education() {
        assertEquals(Family.education(anna, 0), Family.education(anna, 0));
        assertTrue(Family.education(anna, 600) >= Family.education(anna, 0));
        assertTrue(Family.education(anna, 1_000_000) <= 100);
    }

    @Test
    @DisplayName("Gute Paare gewinnen Beziehung, schlechte verlieren sie; Scheidung nur bei schwacher Bindung")
    void dynamics() {
        assertTrue(Family.dailyBondChange(80, false) > 0);
        assertTrue(Family.dailyBondChange(10, true) < 0);
        assertEquals(0, Family.divorceChancePerMille(50, 10, 0, 0));
        assertEquals(0, Family.divorceChancePerMille(0, 90, 0, 0));
        assertTrue(Family.divorceChancePerMille(0, 10, 0, 0) > Family.divorceChancePerMille(0, 10, 100, 100));
        assertEquals(0, Family.birthChancePerMille(Family.CHILD_BOND - 1));
        assertTrue(Family.birthChancePerMille(80) > 0);
    }

    @Test
    @DisplayName("Mehr Bildung heißt weniger Wunschkinder")
    void desiredChildren() {
        assertTrue(Family.desiredChildren(anna, ben, 0, 0) >= Family.desiredChildren(anna, ben, 100, 100));
        assertTrue(Family.desiredChildren(anna, ben, 0, 0) <= Family.MAX_CHILDREN);
    }

    @Test
    @DisplayName("Heirat, Geburt und Scheidung im Register")
    void registry() {
        var registry = FamilyRegistry.empty();
        registry.marry(anna, ben, Day.of(3));
        assertTrue(registry.isMarried(anna));
        assertEquals(ben, registry.spouseOf(anna).orElseThrow());
        assertThrows(IllegalStateException.class, () -> registry.marry(anna, person(3), Day.of(4)));
        assertThrows(IllegalArgumentException.class, () -> registry.marry(person(3), person(3), Day.of(4)));

        var first = registry.birth(anna, Day.of(10));
        var second = registry.birth(ben, Day.of(20));
        assertFalse(first.id().equals(second.id()));
        assertEquals(2, registry.childrenOf(anna).size());
        assertEquals(2, registry.marriageOf(ben).orElseThrow().children());

        registry.divorce(ben, Day.of(30));
        assertFalse(registry.isMarried(anna));
        assertEquals(1, registry.divorces().size());
        assertEquals(2, registry.childrenOf(anna).size(), "Kinder bleiben");
    }

    @Test
    @DisplayName("Beziehung bleibt zwischen 0 und 100; Tod beendet die Ehe ohne Scheidung")
    void bondsAndDeath() {
        var registry = FamilyRegistry.empty();
        assertEquals(100, registry.changeBond(anna, ben, 500));
        assertEquals(0, registry.changeBond(anna, ben, -500));
        registry.changeBond(anna, ben, 30);
        registry.marry(anna, ben, Day.of(1));
        registry.gone(ben, Day.of(2));
        assertFalse(registry.isMarried(anna));
        assertEquals(0, registry.divorces().size());
        assertEquals(0, registry.bond(anna, ben));
    }

    @Test
    @DisplayName("Snapshot stellt das Register wieder her")
    void snapshot() {
        var registry = FamilyRegistry.empty();
        registry.marry(anna, ben, Day.of(1));
        registry.birth(anna, Day.of(5));
        registry.changeBond(anna, ben, 42);
        var restored = FamilyRegistry.restore(registry.snapshot());
        assertEquals(registry.marriages(), restored.marriages());
        assertEquals(42, restored.bond(anna, ben));
        assertEquals(registry.children(), restored.children());
    }
}
