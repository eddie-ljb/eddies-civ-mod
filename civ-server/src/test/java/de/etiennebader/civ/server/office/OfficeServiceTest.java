package de.etiennebader.civ.server.office;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficeServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(43);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Wahl per Befehl: ausrufen, kandidieren, Stimme nach Namen, Auszählung am Tagesende mit Meldung")
    void electionByCommand() {
        foundWithBob();

        assertTrue(server.offices().callElection(caller(alice), "buergermeister").success());
        assertFalse(server.offices().stand(caller(bob), "buergermeister", "kommunismus").success());
        assertTrue(server.offices().stand(caller(bob), "buergermeister", "steuern").success());
        assertFalse(server.offices().vote(caller(alice), "buergermeister", "Niemand").success());
        assertTrue(server.offices().vote(caller(alice), "buergermeister", "bob").success());
        assertTrue(server.offices().vote(caller(bob), "buergermeister", "Bob").success());
        server.takeAnnouncements();

        ElectionView election = server.offices().officesOf(town(), alice).getFirst().election().orElseThrow();
        assertEquals(2, election.ballots());
        assertEquals(bob, election.ownChoice().orElseThrow());
        assertEquals(Platform.LOW_TAXES, election.candidates().getFirst().platform());

        server.endDayNow();
        server.endDayNow();
        assertFalse(town().holds(bob, Office.MAYOR), "ausgezählt wird am Ende von Tag 1 + election_days");
        server.endDayNow();

        assertTrue(town().holds(bob, Office.MAYOR));
        List<Announcement> announced = server.takeAnnouncements();
        assertTrue(announced.stream().anyMatch(a -> a.message().key().equals(MessageKey.ELECTION_WON.key())));
        OfficeView mayor = server.offices().officesOf(town(), alice).getFirst();
        assertEquals("Bob", mayor.holder().orElseThrow());
        assertFalse(mayor.implicit());
    }

    @Test
    @DisplayName("Ohne Abstimmungen ändert nur der Bürgermeister das Recht")
    void onlyMayorDecrees() {
        foundWithBob();

        Reply denied = server.commands().amendLaw(caller(bob), "outsiders_may_build", "ja");
        assertEquals(MessageKey.ONLY_MAYOR_DECREES.key(), denied.lines().getFirst().key());
        assertTrue(server.commands().amendLaw(caller(alice), "outsiders_may_build", "ja").success());
    }

    @Test
    @DisplayName("Bauamt verwaltet Grundstücke, Schatzmeister die Kasse; ernennen und entlassen nur der Bürgermeister")
    void officersGetRights() {
        foundWithBob();
        server.plots().selectCorner(new Caller(bob, "Bob", at(2, 2)), 1);
        server.plots().selectCorner(new Caller(bob, "Bob", at(6, 6)), 2);

        Reply denied = server.plots().createFromSelection(new Caller(bob, "Bob", at(2, 2)), "wohnen");
        assertEquals(MessageKey.ONLY_PLOT_MANAGERS.key(), denied.lines().getFirst().key());

        assertFalse(server.offices().appoint(caller(bob), "bauamt", "Bob").success());
        assertTrue(server.offices().appoint(caller(alice), "bauamt", "Bob").success());
        assertTrue(server.plots().createFromSelection(new Caller(bob, "Bob", at(2, 2)), "wohnen").success());

        assertFalse(server.offices().appoint(caller(alice), "buergermeister", "Bob").success());
        assertFalse(server.offices().appoint(caller(alice), "kaiser", "Bob").success());
        assertTrue(server.offices().dismiss(caller(alice), "bauamt").success());
        assertFalse(server.offices().dismiss(caller(alice), "bauamt").success());
        assertTrue(server.offices().list(caller(bob)).success());
    }

    private void foundWithBob() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.commands().found(caller(alice), "Nordhain");
        server.commands().join(caller(bob), "Nordhain");
    }

    private PolityView town() {
        return server.queries().municipalityOf(alice).orElseThrow();
    }

    private static ChunkCoord chunk() {
        return new ChunkCoord(DimensionId.OVERWORLD, 0, 0);
    }

    private static BlockPosition at(int x, int z) {
        return new BlockPosition(DimensionId.OVERWORLD, x, 64, z);
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", chunk().center(64));
    }
}
