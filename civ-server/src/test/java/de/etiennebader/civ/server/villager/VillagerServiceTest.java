package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerServiceTest {

    private static final Day DAY = Day.of(1);

    private final SeededIdGenerator ids = new SeededIdGenerator(31);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final VillagerService villagers = server.villagers();

    @Test
    @DisplayName("Dorfbewohner ziehen in die Gemeinde, deren Gebiet sie betreten, und bleiben in der Wildnis gemeldet")
    void villagersMoveIntoTowns() {
        found(alice, "Nordhain", 0);
        found(bob, "Eichenfurt", 10);
        PersonRef villager = new PersonRef(PersonId.generate(ids));

        assertFalse(villagers.seen(villager, pos(500, 500), DAY));
        assertTrue(villagers.seen(villager, pos(3, 3), DAY));
        assertEquals("Nordhain", town(villager).name().value());
        assertFalse(villagers.seen(villager, pos(4, 4), DAY));
        assertFalse(villagers.seen(villager, pos(500, 500), DAY));
        assertEquals("Nordhain", town(villager).name().value());

        assertTrue(villagers.seen(villager, pos(163, 163), DAY));
        assertEquals("Eichenfurt", town(villager).name().value());

        villagers.gone(villager, DAY);
        assertTrue(server.queries().municipalityOf(villager).isEmpty());
    }

    @Test
    @DisplayName("Dorfbewohner füllen die Gemeindekasse und heben die Siedlungsstufe")
    void villagersEarnAndPromote() {
        found(alice, "Nordhain", 0);
        List<PersonRef> residents = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            PersonRef villager = new PersonRef(PersonId.generate(ids));
            residents.add(villager);
            villagers.seen(villager, pos(2, 2), DAY);
        }
        PolityView nordhain = town(residents.getFirst());
        assertEquals(9, villagers.villagersOf(nordhain));

        var announcements = villagers.endOfDay(DAY);

        assertEquals(9 * 25, server.queries().ledger().balanceOf(nordhain.asActor(), nordhain.currency()).amount());
        assertEquals(SettlementTier.MARKET_TOWN, server.queries().polity(nordhain.id()).orElseThrow().tier());
        assertEquals(1, announcements.size());
        assertEquals(MessageKey.TOWN_PROMOTED.key(), announcements.getFirst().key());
        assertTrue(villagers.endOfDay(Day.of(2)).isEmpty());
        // 1 Spieler + 9 Dorfbewohner / 4 = 3, dazu 2 als Marktgemeinde: 9 + 4 × 5 Chunks
        assertEquals(29, server.towns().overview(server.queries().polity(nordhain.id()).orElseThrow(), residents.getFirst())
                .claimLimit());
    }

    @Test
    @DisplayName("Dorfbewohner zählen nur zu einem Viertel für die Gebietsgröße")
    void villagersCountQuarterForClaims() {
        found(alice, "Nordhain", 0);
        for (int i = 0; i < 6; i++) {
            villagers.seen(new PersonRef(PersonId.generate(ids)), pos(2, 2), DAY);
        }
        PolityView nordhain = server.queries().municipalityOf(alice).orElseThrow();
        assertEquals(7, nordhain.memberCount());
        assertEquals(2, nordhain.claimWeight());
    }

    private PolityView town(PersonRef villager) {
        return server.queries().municipalityOf(villager).orElseThrow();
    }

    private void found(PlayerRef founder, String name, int chunk) {
        server.commands().found(new Caller(founder, name, new ChunkCoord(DimensionId.OVERWORLD, chunk, chunk).center(64)),
                name);
    }

    private static BlockPosition pos(int x, int z) {
        return new BlockPosition(DimensionId.OVERWORLD, x, 64, z);
    }
}
