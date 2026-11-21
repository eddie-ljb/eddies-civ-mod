package de.etiennebader.civ.server.conjuncture;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.conjuncture.BusinessCycle;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConjunctureServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(91);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Ohne Bund ist die Gemeinde ihre eigene Wirtschaftszone")
    void zoneWithoutFederationIsTownItself() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();

        assertEquals(town.id(), server.conjuncture().zoneOf(town).id());
        assertEquals(BusinessCycle.phaseOf(town.id(), Day.of(10)), server.conjuncture().phaseOf(town, Day.of(10)));
    }

    @Test
    @DisplayName("Mit Bund ist dessen höchste Ebene die Wirtschaftszone, nicht die Gemeinde")
    void zoneWithFederationIsTopAncestor() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.federations().found(caller(alice), "land", "Suedland").success());
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        PolityView land = server.queries().polityNamed("Suedland").orElseThrow();

        assertEquals(land.id(), server.conjuncture().zoneOf(town).id());
    }

    @Test
    @DisplayName("Eine Regierungsmassnahme der Zone (nicht der Gemeinde) daempft den Effekt")
    void stimulusOfZoneDampensEffect() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.federations().found(caller(alice), "land", "Suedland").success());
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();

        double before = server.conjuncture().multiplierOf(town, Day.of(3));
        assertTrue(server.levels().amendLaw(caller(alice), "land", "conjuncture_stimulus_percent", "100").success());
        PolityView refreshedTown = server.queries().polityNamed("Nordhain").orElseThrow();
        double after = server.conjuncture().multiplierOf(refreshedTown, Day.of(3));

        assertTrue(Math.abs(after - 1.0) <= Math.abs(before - 1.0) + 1e-9,
                "Volle Daempfung darf die Abweichung vom Neutralwert nicht vergroessern: " + before + " -> " + after);
    }

    @Test
    @DisplayName("Der Befehl /civ konjunktur meldet Zone, Phase und Effekt der eigenen Gemeinde")
    void conjunctureCommandReportsZone() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());

        var reply = server.villagers().conjunctureReply(caller(alice));

        assertTrue(reply.success());
        assertEquals("Nordhain", reply.lines().getFirst().args().getFirst());
    }

    @Test
    @DisplayName("Ohne bekannte Wirtschaftszonen ist der globale Durchschnitt neutral")
    void globalAverageIsNeutralWithoutZones() {
        assertEquals(1.0, server.conjuncture().globalAverageMultiplier(Day.of(1)));
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}
