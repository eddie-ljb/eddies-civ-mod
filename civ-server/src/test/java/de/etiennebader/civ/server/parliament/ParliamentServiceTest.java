package de.etiennebader.civ.server.parliament;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParliamentServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(83);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Parlament einführen, wählen, anzeigen; Gesetze danach nur noch über das Parlament")
    void lifecycle() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
        assertEquals(MessageKey.PARLIAMENT_NONE.key(),
                server.parliaments().info(caller(alice), Optional.empty()).lines().getFirst().key());
        assertFalse(server.offices().callElection(caller(alice), "parlament").success());

        assertTrue(server.commands().amendLaw(caller(alice), "parliament_seats", "5").success());
        assertTrue(server.offices().callElection(caller(alice), "parlament").success());
        assertTrue(server.parties().found(caller(alice), "ordnung", "Ordnungsbund").success());
        assertTrue(server.offices().stand(caller(alice), "parlament").success());
        assertTrue(server.offices().stand(caller(bob), "parlament").success());
        assertTrue(server.offices().vote(caller(alice), "parlament", "Alice").success());
        assertTrue(server.offices().vote(caller(bob), "parlament", "Alice").success());
        ParliamentView running = server.parliaments().view(nordhain(), alice).orElseThrow();
        assertEquals(2, running.election().orElseThrow().candidates().size());
        assertFalse(running.seated());

        server.takeAnnouncements();
        server.endDayNow();
        server.endDayNow();
        server.endDayNow();
        List<Announcement> news = server.takeAnnouncements();
        assertTrue(news.stream().anyMatch(a -> a.message().key().equals(MessageKey.PARLIAMENT_ELECTED.key())), news::toString);

        ParliamentView view = server.parliaments().view(nordhain(), alice).orElseThrow();
        assertTrue(view.seated());
        assertEquals(1, view.seats(), "Bob hat keine Stimme bekommen");
        assertEquals(Optional.of("Alice"), view.president());
        assertEquals(Optional.of("Ordnungsbund"), view.factions().getFirst().name());
        assertTrue(view.member());
        Reply info = server.parliaments().info(caller(bob), Optional.of("Nordhain"));
        assertEquals(MessageKey.PARLIAMENT_HEADER.key(), info.lines().getFirst().key());

        Reply motion = server.commands().amendLaw(caller(bob), "sales_tax_percent", "5");
        assertTrue(motion.success(), "wird zum Antrag, auch von Nicht-Abgeordneten");
        assertEquals(1, server.parliaments().view(nordhain(), alice).orElseThrow().agenda().size());
        assertFalse(server.voting().vote(caller(bob), 1, true).success(), "Bob hat keinen Sitz");
        assertTrue(server.voting().vote(caller(alice), 1, true).success());
        assertEquals(5L, server.queries().polityNamed("Nordhain").orElseThrow().laws()
                .valueOf(de.etiennebader.civ.domain.law.StandardParameters.SALES_TAX_PERCENT), "1 von 1 Sitzen");
        assertTrue(server.offices().officesOf(server.queries().polityNamed("Nordhain").orElseThrow(), alice).stream()
                .anyMatch(office -> office.office() == Office.PARLIAMENT));
    }

    private de.etiennebader.civ.domain.shared.PolityId nordhain() {
        return server.queries().polityNamed("Nordhain").orElseThrow().id();
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}
