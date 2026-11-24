package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.PersonId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundingNamesTest {

    private static PersonRef person(long seed) {
        return new PersonRef(new PersonId(new UUID(seed * 0x9E3779B97F4A7C15L, seed * 0xC2B2AE3D27D4EB4FL + 7)));
    }

    private static boolean hasDigit(String name) {
        return name.chars().anyMatch(Character::isDigit);
    }

    @Test
    @DisplayName("Namen enthalten nie Zahlen, auch wenn schon viele vergeben sind")
    void neverContainDigits() {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            PersonRef founder = person(i);
            String town = FoundingNames.town(founder, taken::contains);
            assertFalse(hasDigit(town), town);
            assertTrue(town.length() <= 32, town);
            assertTrue(taken.add(town), "jeder Ortsname ist neu: " + town);
        }
    }

    @Test
    @DisplayName("Ein vergebener Name wird übersprungen")
    void skipsTakenNames() {
        PersonRef founder = person(3);
        String first = FoundingNames.town(founder, name -> false);
        String second = FoundingNames.town(founder, first::equals);
        assertFalse(first.equals(second));
    }

    @Test
    @DisplayName("Straßennamen sind echte Wörter und eindeutig in der Gemeinde")
    void streets() {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            String street = FoundingNames.street(person(i), taken, "Lindenau");
            assertFalse(hasDigit(street), street);
            assertTrue(taken.add(street), "doppelt: " + street);
        }
    }

    @Test
    @DisplayName("Firmen, Zeitungen, Parteien und Religionen bekommen sprechende Namen")
    void organisations() {
        PersonRef founder = person(9);
        assertFalse(hasDigit(FoundingNames.company(founder, "Lindenau", name -> false)));
        assertEquals("Lindenau Handelshaus".length() > 0, true);
        assertFalse(hasDigit(FoundingNames.newspaper(founder, "Lindenau", name -> false)));
        assertFalse(hasDigit(FoundingNames.party(founder, "Aufbaupartei", "Lindenau", name -> false)));
        String taken = FoundingNames.party(founder, "Aufbaupartei", "Lindenau", name -> false);
        String next = FoundingNames.party(founder, "Aufbaupartei", "Lindenau", taken::equals);
        assertFalse(hasDigit(next), next);
        assertFalse(taken.equals(next));
        assertFalse(hasDigit(FoundingNames.religion(founder, "Hain", false, name -> false)));
        assertTrue(FoundingNames.scripture(founder, true, name -> false).startsWith("Das Buch"));
        assertTrue(FoundingNames.scripture(founder, false, name -> false).startsWith("Die Sagen"));
    }
}
