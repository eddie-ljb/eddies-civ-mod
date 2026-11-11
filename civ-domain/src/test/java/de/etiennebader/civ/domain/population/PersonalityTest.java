package de.etiennebader.civ.domain.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalityTest {

    private final TestActors actors = new TestActors(7);

    @Test
    @DisplayName("Dieselbe Identität ergibt immer dieselbe Persönlichkeit")
    void deterministic() {
        PersonRef villager = actors.villager();
        assertEquals(Personality.of(villager), Personality.of(new PersonRef(villager.id())));
    }

    @Test
    @DisplayName("Werte häufen sich um 0, alle Eigenschaften kommen vor, Extreme sind selten")
    void distribution() {
        int samples = 2_000;
        long orderSum = 0;
        int extreme = 0;
        Map<Trait, Integer> traits = new EnumMap<>(Trait.class);
        for (int i = 0; i < samples; i++) {
            Personality personality = Personality.of(actors.villager());
            orderSum += personality.order();
            if (Math.abs(personality.thrift()) > 80) {
                extreme++;
            }
            traits.merge(personality.dominant(), 1, Integer::sum);
        }
        assertTrue(Math.abs(orderSum / samples) < 8, "Mittelwert nahe 0: " + orderSum / samples);
        assertTrue(extreme < samples * 8 / 100, "höchstens 8 % extrem: " + extreme);
        assertEquals(Trait.values().length, traits.size(), "alle Eigenschaften kommen vor: " + traits);
    }

    @Test
    @DisplayName("Werte außerhalb von ±100 sind ungültig")
    void bounds() {
        assertThrows(IllegalArgumentException.class, () -> new Personality(101, 0, 0));
        assertEquals(Trait.THRIFTY, new Personality(10, 50, -20).dominant());
        assertEquals(Trait.FREE_SPIRITED, new Personality(-60, 50, -20).dominant());
        assertEquals(Trait.LOYAL, new Personality(10, 5, 70).dominant());
    }
}
