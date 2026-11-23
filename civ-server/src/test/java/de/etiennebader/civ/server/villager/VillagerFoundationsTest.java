package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerFoundationsTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(11);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final Map<PersonRef, BlockPosition> seen = new ConcurrentHashMap<>();
    private final VillagerFoundations foundations = new VillagerFoundations(server.app(),
            villager -> Optional.ofNullable(seen.get(villager)), new Announcements());

    private PersonRef villager(boolean ambitious) {
        while (true) {
            PersonRef candidate = new PersonRef(PersonId.generate(ids));
            if (VillagerAmbitions.isAmbitious(candidate) == ambitious) {
                return candidate;
            }
        }
    }

    private PolityView nordhainWithVillagers(int count, boolean ambitious) {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8)),
                "Nordhain").success());
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        List<PersonRef> villagers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PersonRef villager = villager(ambitious);
            server.app().joinPolity().execute(new JoinPolity.Command(town.id(), villager, MembershipType.CITIZEN,
                    Day.of(1)));
            seen.put(villager, new BlockPosition(DimensionId.OVERWORLD, 1600 + 400 * i, 64, 1600));
            villagers.add(villager);
        }
        return town;
    }

    private long towns() {
        return server.queries().polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL).count();
    }

    @Test
    @DisplayName("Ein ehrgeiziger Dorfbewohner gründet fern der Heimat eine neue Gemeinde und wird ihr Bürgermeister")
    void villagerFoundsTown() {
        nordhainWithVillagers(6, true);
        long before = towns();
        boolean founded = false;
        for (int i = 0; i < 400 && !founded; i++) {
            founded = foundations.endOfDay(Day.of(10 + i));
        }
        assertTrue(founded, "eine Gemeinde entstand");
        assertEquals(before + 1, towns());
        PolityView created = server.queries().polities().stream()
                .filter(p -> p.kind() == PolityKind.TERRITORIAL && p.name().value().startsWith("Neustadt"))
                .findFirst().orElseThrow();
        // Bis zur ersten Wahl führt der Gründer als kommissarischer Bürgermeister (villagerPolitics).
        assertTrue(created.mayor(true).filter(PersonRef.class::isInstance).isPresent(), "ein Dorfbewohner führt sie");
        PersonRef founder = (PersonRef) created.mayor(true).orElseThrow();
        assertEquals(created.id(), server.queries().municipalityOf(founder).orElseThrow().id());
        assertEquals(1 + VillagerFoundations.MAX_FOLLOWERS, created.members().size(), "Gefolge zieht mit");
        assertEquals(7 - created.members().size(),
                server.queries().polityNamed("Nordhain").orElseThrow().members().size(), "Rest bleibt in Nordhain");
        assertTrue(created.seat().isPresent(), "das Gemeindehaus steht am letzten Aufenthaltsort");
    }

    @Test
    @DisplayName("Mit Gründungsgebühr gründen mittellose Dorfbewohner keine Gemeinde")
    void poorVillagersCannotAffordFounding() {
        nordhainWithVillagers(6, true);
        VillagerFoundations paying = new VillagerFoundations(server.app(),
                villager -> Optional.ofNullable(seen.get(villager)), new Announcements(),
                de.etiennebader.civ.server.founding.FoundingFees.DEFAULT);
        for (int i = 0; i < 300; i++) {
            assertFalse(paying.endOfDay(Day.of(10 + i)));
        }
        assertEquals(1, towns());
        assertEquals(1, server.queries().polityNamed("Nordhain").orElseThrow().members().stream()
                .filter(m -> m.member() instanceof de.etiennebader.civ.domain.actor.PlayerRef).count() + 0);
    }

    @Test
    @DisplayName("Ohne Ehrgeiz oder in kleinen Gemeinden gründet niemand etwas")
    void nothingWithoutAmbitionOrSize() {
        nordhainWithVillagers(6, false);
        for (int i = 0; i < 200; i++) {
            assertFalse(foundations.endOfDay(Day.of(10 + i)));
        }
        assertEquals(1, towns());
    }

    @Test
    @DisplayName("Wer mitten in der Heimatgemeinde lebt, gründet an einem freien Ort in der Nähe und zieht dorthin")
    void villagerInsideHomeTerritoryFoundsNearby() {
        PolityView town = nordhainWithVillagers(6, true);
        BlockPosition inside = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8); // im beanspruchten Kerngebiet
        seen.replaceAll((villager, position) -> inside);
        java.util.List<java.util.Map.Entry<PersonRef, BlockPosition>> relocated = new ArrayList<>();
        VillagerFoundations relocating = new VillagerFoundations(server.app(),
                villager -> Optional.ofNullable(seen.get(villager)), new Announcements(),
                de.etiennebader.civ.server.founding.FoundingFees.FREE,
                (villager, seat) -> relocated.add(java.util.Map.entry(villager, seat)));
        boolean founded = false;
        for (int i = 0; i < 400 && !founded; i++) {
            founded = relocating.endOfDay(Day.of(10 + i));
        }
        assertTrue(founded, "trotz Wohnsitz im fremden Gebiet entsteht eine Gemeinde");
        assertEquals(2, towns());
        PolityView created = server.queries().polities().stream()
                .filter(p -> p.kind() == PolityKind.TERRITORIAL && p.name().value().startsWith("Neustadt"))
                .findFirst().orElseThrow();
        BlockPosition seat = created.seat().orElseThrow();
        assertTrue(server.queries().territory().sovereignOf(seat.chunk()).filter(created.id()::equals).isPresent(),
                "das Gemeindehaus liegt im eigenen Gebiet");
        assertTrue(Math.abs(seat.chunk().x()) > VillagerFoundations.SITE_GAP
                || Math.abs(seat.chunk().z()) > VillagerFoundations.SITE_GAP, "mit Abstand zur Heimatgemeinde");
        assertEquals(created.members().size(), relocated.size(), "Gründer und Gefolge werden zum Gemeindehaus gebracht");
        assertTrue(relocated.stream().allMatch(entry -> entry.getValue().equals(seat)));
        assertEquals(7, created.members().size() + server.queries().polity(town.id()).orElseThrow().members().size());
    }
}
