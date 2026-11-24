package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerMediaFoundingTest {

    private static final BlockPosition HERE = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);

    private final SeededIdGenerator ids = new SeededIdGenerator(31);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    private void populate(int count) {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(new Caller(alice, "Alice", HERE), "Nordhain").success());
        for (int i = 0; i < count; i++) {
            server.villagers().seen(new PersonRef(PersonId.generate(ids)), HERE, Day.of(1));
        }
    }

    @Test
    @DisplayName("Ehrgeizige Dorfbewohner gründen eine Zeitung mit Redaktion")
    void villagersFoundNewspaper() {
        populate(40);
        boolean founded = false;
        for (int day = 2; day < 400 && !founded; day++) {
            founded = server.press().villagersFound(Day.of(day));
        }
        assertTrue(founded, "irgendwann entsteht eine Zeitung");
        var papers = server.queries().newspapers();
        assertEquals(1, papers.size());
        assertTrue(papers.getFirst().mayor().filter(PersonRef.class::isInstance).isPresent(), "ein Dorfbewohner leitet sie");
        assertTrue(VillagerAmbitions.isAmbitious((PersonRef) papers.getFirst().mayor().orElseThrow()));
        assertTrue(papers.getFirst().members().size() >= 2, "mit Mitbürgern in der Redaktion");
    }

    @Test
    @DisplayName("Ohne Dorfbewohner entsteht keine Zeitung und keine Religion")
    void nothingWithoutVillagers() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(new Caller(alice, "Alice", HERE), "Nordhain").success());
        for (int day = 2; day < 200; day++) {
            assertFalse(server.press().villagersFound(Day.of(day)));
            assertFalse(server.religion().villagersFound(Day.of(day)));
        }
    }

    @Test
    @DisplayName("Sehr gläubige Dorfbewohner gründen eine Religion und führen sie")
    void villagersFoundReligion() {
        populate(40);
        boolean founded = false;
        for (int day = 2; day < 600 && !founded; day++) {
            founded = server.religion().villagersFound(Day.of(day));
        }
        assertTrue(founded, "irgendwann entsteht eine Religion");
        var entries = server.religion().entries(alice);
        assertEquals(1, entries.size());
        var religion = entries.getFirst().religion();
        assertTrue(religion.clergyHead().filter(PersonRef.class::isInstance).isPresent());
        PersonRef founder = (PersonRef) religion.clergyHead().orElseThrow();
        assertTrue(Personality.of(founder).religiosity() >= 35);
        assertEquals(1, entries.getFirst().members());
        // Höchstens eine Religion je zwölf Dorfbewohner
        for (int day = 700; day < 900; day++) {
            server.religion().villagersFound(Day.of(day));
        }
        assertTrue(server.religion().entries(alice).size() <= Math.max(1, 41 / 12));
    }
}
