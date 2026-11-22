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

    private static PersonRef person(String uuid) {
        return new PersonRef(new PersonId(UUID.fromString(uuid)));
    }
}
