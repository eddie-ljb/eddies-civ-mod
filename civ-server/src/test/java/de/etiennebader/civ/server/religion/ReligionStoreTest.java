package de.etiennebader.civ.server.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.religion.Denomination;
import de.etiennebader.civ.domain.religion.Religion;
import de.etiennebader.civ.domain.religion.ReligionId;
import de.etiennebader.civ.domain.religion.ReligionRegistry;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReligionStoreTest {

    @Test
    void religionElectionAndMembersSurviveRoundTrip(@TempDir Path dir) {
        PlayerRef founder = new PlayerRef(new PlayerId(UUID.randomUUID()));
        PersonRef villager = new PersonRef(new PersonId(UUID.randomUUID()));
        ReligionRegistry registry = ReligionRegistry.empty();
        ReligionId id = new ReligionId(UUID.randomUUID());
        Day day = Day.of(5);
        registry.found(id, "Sonnenkult", founder, false, "Sonnenbuch", "Sonnentempel", "Latein", day);
        registry.addDenomination(id, new Denomination("Morgen"), day);
        registry.callClergyElection(id, Day.of(8), day);
        registry.nominateClergy(id, founder, day);
        registry.castClergyVote(id, villager, founder, day);
        Map<ActorRef, ReligionId> members = new LinkedHashMap<>();
        members.put(founder, id);
        members.put(villager, id);

        ReligionStore store = new ReligionStore(Optional.of(dir.resolve("religion.tsv")));
        store.save(new ReligionStore.State(registry.religions(), members, Map.of(villager, "Morgen"),
                List.of("Taufe: Eddie"), Map.of(id, 4), Map.of(id, "Sonne")));
        ReligionStore.State loaded = store.load();

        assertEquals(1, loaded.religions().size());
        Religion r = loaded.religions().getFirst();
        assertEquals("Sonnenkult", r.name());
        assertEquals(List.of(new Denomination("Morgen")), r.denominations());
        assertEquals(Optional.of(founder), r.clergyHead());
        assertTrue(r.election().isPresent());
        assertEquals(Optional.of(founder), r.election().get().winner());
        assertEquals(members, loaded.members());
        assertEquals("Morgen", loaded.denominations().get(villager));
        assertEquals(List.of("Taufe: Eddie"), loaded.rites());
        assertEquals(4, loaded.fervor().get(id));
        assertEquals("Sonne", loaded.logos().get(id));
    }

    @Test
    void scriptureRolesAndBlessingsSurviveRoundTrip(@TempDir Path dir) {
        PlayerRef founder = new PlayerRef(new PlayerId(UUID.randomUUID()));
        PersonRef villager = new PersonRef(new PersonId(UUID.randomUUID()));
        ReligionRegistry registry = ReligionRegistry.empty();
        ReligionId id = new ReligionId(UUID.randomUUID());
        registry.found(id, "Hain", founder, false, "Die Sagen", "Heiliger Hain", "Altdeutsch", Day.of(1));
        ClergyRole role = new ClergyRole(id, "Priester", java.util.Set.of(ClergyRole.Right.BLESS,
                ClergyRole.Right.SCRIPTURE), java.util.Set.of(villager));

        ReligionStore store = new ReligionStore(Optional.of(dir.resolve("religion.tsv")));
        store.save(new ReligionStore.State(registry.religions(), Map.of(founder, id), Map.of(), List.of(), Map.of(),
                Map.of(), Map.of(id, "Am Anfang war der Hain.\nDann kam der Fluss.\tEnde"), List.of(role),
                Map.of(villager, 9L)));
        ReligionStore.State loaded = store.load();

        assertEquals("Am Anfang war der Hain.\nDann kam der Fluss.\tEnde", loaded.texts().get(id));
        assertEquals(1, loaded.roles().size());
        assertEquals("Priester", loaded.roles().getFirst().name());
        assertEquals(java.util.Set.of(ClergyRole.Right.BLESS, ClergyRole.Right.SCRIPTURE),
                loaded.roles().getFirst().rights());
        assertEquals(java.util.Set.of(villager), loaded.roles().getFirst().holders());
        assertEquals(9L, loaded.blessed().get(villager));
    }
}
