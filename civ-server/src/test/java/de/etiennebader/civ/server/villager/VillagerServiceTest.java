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
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        long productivity = villagers.moodOf(nordhain).productivity();
        assertTrue(productivity >= 9 * 50 && productivity <= 9 * 150, "je Dorfbewohner 50 bis 150 %");
        var announcements = villagers.endOfDay(DAY);

        assertEquals(25 * productivity / 100,
                server.queries().ledger().balanceOf(nordhain.asActor(), nordhain.currency()).amount());
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

    @Test
    @DisplayName("Stimmung: /civ stimmung fasst zusammen, ein einzelner Dorfbewohner gibt Auskunft über sich")
    void moodReport() {
        found(alice, "Nordhain", 0);
        Caller caller = new Caller(alice, "Alice", pos(2, 2));
        assertEquals(MessageKey.MOOD_NONE.key(), villagers.mood(caller).lines().getFirst().key());

        PersonRef villager = new PersonRef(PersonId.generate(ids));
        villagers.seen(villager, pos(2, 2), DAY);
        Reply mood = villagers.mood(caller);
        assertEquals(5, mood.lines().size());
        assertEquals(MessageKey.MOOD_HEADER.key(), mood.lines().getFirst().key());
        assertEquals(6, mood.lines().get(2).args().size(), "eine Zahl je Eigenschaft");

        Reply info = villagers.describe(villager);
        assertEquals(MessageKey.VILLAGER_INFO.key(), info.lines().getFirst().key());
        assertEquals(MessageKey.VILLAGER_MOOD.key(), info.lines().get(1).key());
        assertTrue(villagers.satisfactionOf(villager).isPresent());

        PersonRef stranger = new PersonRef(PersonId.generate(ids));
        assertEquals(MessageKey.VILLAGER_HOMELESS.key(), villagers.describe(stranger).lines().getFirst().key());
    }

    @Test
    @DisplayName("Abwanderung: unter erdrückenden Steuern ziehen Dorfbewohner weg; das Spiel holt sie ab")
    void emigration() {
        ServerSettings base = CivServerFixture.SETTINGS;
        CivServerFixture server = CivServerFixture.start(ids, new ServerSettings(base.ticksPerDay(), base.claimBase(),
                base.claimPerMember(), base.currency(), base.priceReference(), base.villagerIncome(),
                new ServerSettings.TierThresholds(500, 600, 700), base.web(), true));
        VillagerService villagers = server.villagers();
        PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
        server.commands().found(new Caller(alice, "Nordhain", new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64)),
                "Nordhain");
        server.commands().found(new Caller(bob, "Eichenfurt", new ChunkCoord(DimensionId.OVERWORLD, 10, 10).center(64)),
                "Eichenfurt");
        Caller mayor = new Caller(alice, "Alice", pos(2, 2));
        server.commands().amendLaw(mayor, "sales_tax_percent", "50");
        server.commands().amendLaw(mayor, "land_tax_per_block", "10");
        server.commands().amendLaw(mayor, "explosions_allowed", "ja");
        server.commands().amendLaw(mayor, "fire_spread_allowed", "ja");
        server.commands().amendLaw(new Caller(bob, "Bob", pos(163, 163)), "villager_vote_percent", "100");
        List<PersonRef> residents = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            PersonRef villager = new PersonRef(PersonId.generate(ids));
            villagers.seen(villager, pos(2, 2), DAY);
            residents.add(villager);
        }
        server.takeAnnouncements();
        PolityView nordhain = server.queries().municipalityOf(residents.getFirst()).orElseThrow();
        assertTrue(villagers.moodOf(nordhain).atRisk() > 0, "manche denken ans Gehen");

        for (long day = 1; day <= 30 && !villagers.hasDepartures(); day++) {
            villagers.endOfDay(Day.of(day));
        }
        assertTrue(villagers.hasDepartures(), "jemand wandert ab");
        List<Announcement> news = server.takeAnnouncements();
        assertTrue(news.stream().anyMatch(a -> a.message().key().equals(MessageKey.VILLAGERS_LEFT.key())
                || a.message().key().equals(MessageKey.VILLAGERS_MOVED_AWAY.key())));

        PersonRef departed = residents.stream()
                .filter(villager -> server.queries().municipalityOf(villager)
                        .map(town -> !town.name().value().equals("Nordhain")).orElse(true))
                .findFirst().orElseThrow();
        assertFalse(villagers.seen(departed, pos(2, 2), DAY), "steht noch im Ort, zählt aber nicht mehr");
        VillagerService.PendingDeparture pending = villagers.takeDeparture(departed).orElseThrow();
        Optional<PolityView> newHome = server.queries().municipalityOf(departed);
        assertEquals(newHome.flatMap(PolityView::seat), pending.destination());
        assertTrue(villagers.takeDeparture(departed).isEmpty());
    }

    private static BlockPosition pos(int x, int z) {
        return new BlockPosition(DimensionId.OVERWORLD, x, 64, z);
    }
}
