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
        assertEquals(1, created.members().size());
        assertTrue(created.seat().isPresent(), "das Gemeindehaus steht am letzten Aufenthaltsort");
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
    @DisplayName("Schlägt die Claim-Prüfung fehl, bleibt der Dorfbewohner in seiner Gemeinde")
    void rejectedClaimKeepsMembership() {
        PolityView town = nordhainWithVillagers(6, true);
        seen.replaceAll((villager, position) -> new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8)); // schon beansprucht
        for (int i = 0; i < 300; i++) {
            assertFalse(foundations.endOfDay(Day.of(10 + i)));
        }
        assertEquals(1, towns());
        assertEquals(7, server.queries().polity(town.id()).orElseThrow().members().size());
    }
}
