package de.etiennebader.civ.server.war;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.war.MilitaryRank;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(27);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carol = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    private Caller caller(PlayerRef player, String name, int x) {
        return new Caller(player, name, new BlockPosition(DimensionId.OVERWORLD, x, 64, x));
    }

    private Caller a() {
        return caller(alice, "Alice", 8);
    }

    private Caller b() {
        return caller(bob, "Bob", 800);
    }

    private void found() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(carol, "Carol");
        assertTrue(server.commands().found(a(), "Nordhain").success());
        assertTrue(server.commands().found(b(), "Suedhain").success());
    }

    private PolityId id(String name) {
        return server.queries().polityNamed(name).orElseThrow().id();
    }

    @Test
    @DisplayName("Nur der Buergermeister erklaert Krieg; doppelt und gegen sich selbst geht nicht")
    void declareRules() {
        found();
        assertFalse(server.war().declare(caller(carol, "Carol", 8), "Suedhain").success());
        assertFalse(server.war().declare(a(), "Nordhain").success());
        assertFalse(server.war().declare(a(), "Nirgendwo").success());
        assertTrue(server.war().declare(a(), "Suedhain").success());
        assertFalse(server.war().declare(b(), "Nordhain").success());
        assertEquals(1, server.war().registry().wars().size());
    }

    @Test
    @DisplayName("Die groessere Garnison gewinnt das Gefecht und der Krieg endet")
    void battleEndsWar() {
        found();
        server.war().declare(a(), "Suedhain");
        assertTrue(server.war().enlist(a(), MilitaryRank.OFFICER).success());
        assertFalse(server.war().enlist(a(), MilitaryRank.SOLDIER).success());
        assertTrue(server.war().enlist(b(), MilitaryRank.SOLDIER).success());

        assertFalse(server.war().battle(caller(carol, "Carol", 8), 1).success());
        assertTrue(server.war().battle(a(), 1).success());
        var war = server.war().registry().wars().get(0);
        assertFalse(war.ongoing());
        assertEquals(id("Nordhain"), war.outcome().orElseThrow().winner());
        assertFalse(server.war().battle(a(), 1).success());
    }

    @Test
    @DisplayName("Gleichstand laesst den Krieg weiterlaufen, Frieden beendet ihn ohne Sieger")
    void drawThenPeace() {
        found();
        server.war().declare(a(), "Suedhain");
        assertTrue(server.war().battle(a(), 1).success());
        assertTrue(server.war().registry().wars().get(0).ongoing());
        assertTrue(server.war().peace(b(), 1).success());
        assertFalse(server.war().registry().wars().get(0).ongoing());
        assertTrue(server.war().registry().wars().get(0).outcome().isEmpty());
    }

    @Test
    @DisplayName("Neutralitaet braucht einen uebergeordneten Bund und schuetzt vor Kriegserklaerung")
    void neutrality() {
        found();
        assertFalse(server.war().neutrality(b(), 5).success());
        assertTrue(server.federations().found(b(), "land", "Suedland").success());
        // die leere Gemeindekasse kann die Standardgebühr nicht bezahlen
        assertFalse(server.war().neutrality(b(), 5).success());
        assertTrue(server.levels().amendLaw(b(), "land", "neutrality_fee_per_day", "0").success());
        assertTrue(server.war().neutrality(b(), 5).success());
        assertFalse(server.war().declare(a(), "Suedhain").success());
    }

    @Test
    @DisplayName("Entlassen aus der Garnison")
    void discharge() {
        found();
        assertFalse(server.war().discharge(a()).success());
        server.war().enlist(a(), MilitaryRank.SOLDIER);
        assertTrue(server.war().discharge(a()).success());
    }
}
