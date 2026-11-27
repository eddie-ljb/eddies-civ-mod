package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.population.Occupation;
import de.etiennebader.civ.domain.shared.PersonId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerOccupationsTest {

    @Test
    void theRoleWithTheHighestPrecedenceWinsAndPlayersAreIgnored() {
        VillagerOccupations occupations = VillagerOccupations.empty();
        PersonRef mayorAndFounder = new PersonRef(new PersonId(UUID.randomUUID()));
        PersonRef soldier = new PersonRef(new PersonId(UUID.randomUUID()));
        occupations.claim(mayorAndFounder, Occupation.ENTREPRENEUR);
        occupations.claim(mayorAndFounder, Occupation.POLITICIAN);
        occupations.claim(mayorAndFounder, Occupation.WORKER);
        occupations.claim(soldier, Occupation.SOLDIER);
        occupations.claim(soldier, Occupation.CIVILIAN);
        occupations.claim((ActorRef) PlayerRef.of(UUID.randomUUID()), Occupation.POLICE);

        Map<PersonRef, Occupation> assigned = occupations.assigned();
        assertEquals(2, assigned.size());
        assertEquals(Occupation.POLITICIAN, assigned.get(mayorAndFounder));
        assertEquals(Occupation.SOLDIER, assigned.get(soldier));
        assertTrue(assigned.values().stream().allMatch(Occupation::dressed));
    }
}
