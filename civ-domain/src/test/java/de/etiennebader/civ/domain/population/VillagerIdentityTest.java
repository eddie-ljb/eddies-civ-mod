package de.etiennebader.civ.domain.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.PersonId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerIdentityTest {

    @Test
    void identityIsStableAndDoesNotTruncateTheUuid() {
        PersonRef first = person("ffffffff-ffff-4fff-bfff-ffffffffffff");
        PersonRef second = person("ffffffff-ffff-4fff-bfff-fffffffffffe");
        VillagerIdentity identity = VillagerIdentity.of(first);

        assertEquals(identity, VillagerIdentity.of(person(first.uuid().toString())));
        assertEquals(identity.fullName(), VillagerIdentity.nameOf(first.uuid()));
        assertTrue(identity.fullName().matches("\\p{L}+ \\p{L}+"));
        assertTrue(identity.registerNumber().matches("V-[0-9]+"));
        assertNotEquals(identity.registerNumber(), VillagerIdentity.of(second).registerNumber());
    }

    @Test
    void genderFollowsTheFirstNameAndLookIsStable() {
        java.util.Set<String> female = java.util.Set.of("Ada", "Alina", "Anna", "Aylin", "Clara", "Dilan", "Elif",
                "Emma", "Frieda", "Hannah", "Ida", "Jana", "Klara", "Lea", "Leonie", "Leyla", "Lotte", "Mara", "Mila",
                "Mina", "Nele", "Nora", "Rosa", "Samira", "Selin", "Sofia", "Yara", "Zoe");
        java.util.Set<Gender> seen = new java.util.HashSet<>();
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 500; i++) {
            UUID uuid = new UUID(random.nextLong(), random.nextLong());
            String first = VillagerIdentity.nameOf(uuid).split(" ")[0];
            Gender gender = VillagerIdentity.genderOf(uuid);
            assertEquals(female.contains(first) ? Gender.FEMALE : Gender.MALE, gender, first);
            seen.add(gender);
            VillagerIdentity.Look look = VillagerIdentity.lookOf(uuid);
            assertEquals(look, VillagerIdentity.lookOf(uuid));
            assertEquals(gender, look.gender());
            assertTrue(look.skin() >= 0 && look.skin() < VillagerIdentity.SKIN_TONES);
            assertTrue(look.hair() >= 0 && look.hair() < VillagerIdentity.HAIR_COLORS);
            assertTrue(look.style() >= 0 && look.style() < VillagerIdentity.HAIR_STYLES);
        }
        assertEquals(java.util.Set.of(Gender.FEMALE, Gender.MALE), seen);
    }

    private static PersonRef person(String uuid) {
        return new PersonRef(new PersonId(UUID.fromString(uuid)));
    }
}
