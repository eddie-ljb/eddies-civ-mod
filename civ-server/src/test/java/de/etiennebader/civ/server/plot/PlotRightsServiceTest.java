package de.etiennebader.civ.server.plot;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.protection.PlotFlag;
import de.etiennebader.civ.domain.protection.ProtectedAction;
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

class PlotRightsServiceTest {

    private static final BlockPosition HERE = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);
    /** Im selben Grundstück, aber nicht das Gemeindehaus selbst, das niemand verändern darf. */
    private static final BlockPosition SPOT = new BlockPosition(DimensionId.OVERWORLD, 10, 64, 10);

    private final SeededIdGenerator ids = new SeededIdGenerator(44);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef cleo = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final Caller aliceCaller = new Caller(alice, "Alice", HERE);
    private final Caller bobCaller = new Caller(bob, "Bob", HERE);
    private final Caller cleoCaller = new Caller(cleo, "Cleo", HERE);

    private void setUp() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(cleo, "Cleo");
        assertTrue(server.commands().found(aliceCaller, "Nordhain").success());
        assertTrue(server.plots().createChunk(aliceCaller, "wohnen").success());
        assertTrue(server.plots().assign(aliceCaller, "Bob").success());
    }

    @Test
    @DisplayName("Nur der Inhaber stellt die Rechte ein; sie wirken sofort auf den Schutz")
    void holderSetsRights() {
        setUp();
        var protection = server.protection();
        assertTrue(protection.check(cleo, ProtectedAction.OPEN_CONTAINER, SPOT).isPresent(), "vorher gesperrt");

        assertFalse(server.plotRights().setAudience(aliceCaller, false, "behaelter", "ja").success(),
                "Alice ist nur Bürgermeisterin, nicht Inhaberin");
        assertTrue(server.plotRights().setAudience(bobCaller, false, "behaelter", "ja").success());

        assertTrue(protection.check(cleo, ProtectedAction.OPEN_CONTAINER, SPOT).isEmpty(), "jetzt erlaubt");
        assertTrue(protection.check(cleo, ProtectedAction.BUILD, SPOT).isPresent(), "Bauen bleibt verboten");

        assertTrue(server.plotRights().setGuest(bobCaller, "Cleo", "bauen", "ja").success());
        assertTrue(protection.check(cleo, ProtectedAction.BUILD, SPOT).isEmpty(), "als Gast darf sie bauen");
        assertTrue(server.plotRights().removeGuest(bobCaller, "Cleo").success());
        assertTrue(protection.check(cleo, ProtectedAction.BUILD, SPOT).isPresent());
        assertTrue(server.plotRights().reset(bobCaller).success());
        assertTrue(protection.check(cleo, ProtectedAction.OPEN_CONTAINER, SPOT).isPresent());
    }

    @Test
    @DisplayName("Regeln des Grundstücks überschreiben das Gesetz, solange es erlaubt ist")
    void rulesOverrideLaw() {
        setUp();
        var protection = server.protection();
        assertTrue(protection.allows(de.etiennebader.civ.domain.protection.Hazard.EXPLOSION, SPOT) == false);
        assertTrue(server.plotRights().setRule(bobCaller, "explosionen", "erlauben").success());
        assertTrue(protection.allows(de.etiennebader.civ.domain.protection.Hazard.EXPLOSION, SPOT));
        assertTrue(protection.allowsPvp(SPOT));
        assertTrue(server.plotRights().setRule(bobCaller, "pvp", "verbieten").success());
        assertFalse(protection.allowsPvp(SPOT));
        assertFalse(server.plotRights().setRule(bobCaller, "wetter", "verbieten").success());
        var view = server.plotRights().viewAt(bob, HERE).orElseThrow();
        assertTrue(view.canManage());
        assertEquals(PlotFlag.values().length, view.rules().size());
    }

    @Test
    @DisplayName("Wechselt das Grundstück den Inhaber, gelten wieder die Standardrechte")
    void rightsDoNotSurviveSale() {
        setUp();
        assertTrue(server.plotRights().setAudience(bobCaller, false, "bauen", "ja").success());
        assertTrue(server.protection().check(cleo, ProtectedAction.BUILD, SPOT).isEmpty());
        var plot = server.plots().plotAt(bob, HERE).orElseThrow();
        assertTrue(server.plots().offer(bobCaller, "1").success());
        server.bank().deposit(cleoCaller, 5);
        assertTrue(server.plots().buyHere(cleoCaller).success(), "Cleo kauft das Grundstück");
        assertEquals(plot.id(), server.plots().plotAt(cleo, HERE).orElseThrow().id());
        assertTrue(server.protection().check(bob, ProtectedAction.BUILD, SPOT).isPresent(),
                "die alten Einstellungen gelten für den neuen Inhaber nicht");
    }

    @Test
    @DisplayName("Auch Dorfbewohner der Gemeinde lassen sich als Gast eintragen")
    void villagerGuest() {
        setUp();
        var villager = new de.etiennebader.civ.domain.actor.PersonRef(de.etiennebader.civ.domain.shared.PersonId.generate(ids));
        server.villagers().seen(villager, HERE, de.etiennebader.civ.domain.shared.Day.of(1));
        String name = de.etiennebader.civ.domain.population.VillagerIdentity.of(villager).fullName();
        assertTrue(server.plotRights().setGuest(bobCaller, name, "bauen", "ja").success());
        assertTrue(server.protection().check(cleo, ProtectedAction.BUILD, SPOT).isPresent());
        assertTrue(server.plotRights().viewAt(bob, HERE).orElseThrow().rights().guests().containsKey(villager));
    }
}
