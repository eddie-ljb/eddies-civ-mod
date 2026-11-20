package de.etiennebader.civ.server.level;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryColorTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(21);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Ohne Filter färbt die höchste Ebene, mit Filter genau die gewählte; ohne Bund die Gemeinde selbst")
    void groupOf() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(caller(), "Nordhain").success());
        PolityView standalone = named("Nordhain");

        assertEquals(standalone.id(),
                TerritoryColor.groupOf(standalone, server.queries().ancestorsOf(standalone.id()), Optional.empty()),
                "ohne Bund bleibt die Gemeinde selbst die Farbgruppe");

        assertTrue(server.federations().found(caller(), "land", "Westmark").success());
        assertTrue(server.federations().found(caller(), "nation", "Reich").success());
        PolityView town = named("Nordhain");
        PolityView land = named("Westmark");
        PolityView nation = named("Reich");
        List<PolityView> ancestors = server.queries().ancestorsOf(town.id());

        assertEquals(nation.id(), TerritoryColor.groupOf(town, ancestors, Optional.empty()),
                "ohne Filter gilt die höchste Ebene");
        assertEquals(nation.id(), TerritoryColor.groupOf(town, ancestors, Optional.of("nation")));
        assertEquals(land.id(), TerritoryColor.groupOf(town, ancestors, Optional.of("land")));
        assertEquals(town.id(), TerritoryColor.groupOf(town, ancestors, Optional.of("gemeinde")),
                "Gemeinde ist kein Vorfahr, fällt also auf sich selbst zurück");
        assertEquals(town.id(), TerritoryColor.groupOf(town, ancestors, Optional.of("kreis")),
                "unbekannte Ebene fällt auf die Gemeinde zurück");
    }

    private PolityView named(String name) {
        return server.queries().polityNamed(name).orElseThrow();
    }

    private Caller caller() {
        return new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}
