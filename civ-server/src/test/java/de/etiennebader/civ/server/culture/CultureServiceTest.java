package de.etiennebader.civ.server.culture;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.population.Culture;
import de.etiennebader.civ.domain.population.Race;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultureServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(5);
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final Caller aliceAt = new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    private final Caller bobAt = new Caller(bob, "Bob", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));

    private void found() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(aliceAt, "Nordhain").success());
    }

    @Test
    @DisplayName("Standard: Mensch der Ebene; der Bürgermeister ändert beides, andere nicht")
    void mayorChangesCultureAndRace() {
        found();
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertEquals(new CultureService.Character(Culture.PLAINS, Race.HUMAN), server.culture().of(town.id()));
        assertTrue(server.culture().set(aliceAt, "wueste", "zwerg").success());
        assertEquals(new CultureService.Character(Culture.DESERT, Race.DWARF), server.culture().of(town.id()));
        assertTrue(server.culture().set(aliceAt, Optional.of(Culture.SNOW), Optional.empty()).success());
        assertEquals(new CultureService.Character(Culture.SNOW, Race.DWARF), server.culture().of(town.id()));
        assertFalse(server.culture().set(aliceAt, Optional.of(Culture.SNOW), Optional.empty()).success(), "unverändert");
        assertFalse(server.culture().set(aliceAt, "mars", "zwerg").success());
        assertFalse(server.culture().set(aliceAt, "taiga", "drache").success());
        assertFalse(server.culture().set(bobAt, "taiga", "elf").success(), "Bob gehört nicht zur Gemeinde");
        assertTrue(server.culture().describe(aliceAt).success());
    }

    @Test
    @DisplayName("Eine Tochtergemeinde erbt Kultur und Rasse; der Zustand bleibt über einen Neustart erhalten")
    void inheritanceAndPersistence(@TempDir Path dir) {
        found();
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        Path file = dir.resolve("culture.tsv");
        CultureService service = new CultureService(server.queries(), new Announcements(), Optional.of(file));
        assertTrue(service.set(aliceAt, "taiga", "elf").success());
        var created = de.etiennebader.civ.domain.shared.PolityId.generate(ids);
        service.inherit(town.id(), created);
        CultureService restored = new CultureService(server.queries(), new Announcements(), Optional.of(file));
        assertEquals(new CultureService.Character(Culture.TAIGA, Race.ELF), restored.of(town.id()));
        assertEquals(new CultureService.Character(Culture.TAIGA, Race.ELF), restored.of(created));
    }

    @Test
    @DisplayName("Wörter deutsch und englisch; Rasse aus der Körpergröße")
    void parsingAndScale() {
        assertEquals(Optional.of(Culture.DESERT), Culture.parse("Wüste"));
        assertEquals(Optional.of(Culture.DESERT), Culture.parse("desert"));
        assertEquals(Optional.of(Race.ORC), Race.parse("ork"));
        assertTrue(Culture.parse("nix").isEmpty());
        for (Race race : Race.values()) {
            assertEquals(race, Race.fromScale(race.scale()));
        }
        assertEquals(Race.HUMAN, Race.fromScale(0.5F), "Babys gelten als Mensch");
    }
}
