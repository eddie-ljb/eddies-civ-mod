package de.etiennebader.civ.server.campaign;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
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

class CampaignServiceTest {

    private static final BlockPosition HERE = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);

    private final SeededIdGenerator ids = new SeededIdGenerator(85);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Rede, Versprechen, Plakat, Umfrage und Ergebnis über den Wahlkampf-Dienst")
    void campaign() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
        for (int i = 0; i < 3; i++) {
            server.villagers().seen(new PersonRef(PersonId.generate(ids)), HERE, Day.of(1));
        }

        assertEquals(MessageKey.SPEECH_NO_EFFECT.key(),
                server.campaigns().speak(caller(bob), "Freiheit!", List.of()).lines().getFirst().key());
        assertEquals(MessageKey.PROMISE_NOT_RUNNING.key(),
                server.campaigns().promise(caller(bob), "Steuern runter").lines().getFirst().key());
        assertEquals(MessageKey.POLL_NONE.key(),
                server.campaigns().poll(caller(bob), Optional.empty()).lines().getFirst().key());

        assertTrue(server.offices().callElection(caller(alice), "buergermeister").success());
        assertTrue(server.offices().stand(caller(bob), "buergermeister").success());
        assertTrue(server.offices().stand(caller(alice), "buergermeister").success());
        Reply speech = server.campaigns().speak(caller(bob), "Mehr Freiheit für alle!", List.of());
        assertEquals(MessageKey.SPEECH_HEARD.key(), speech.lines().getFirst().key());
        assertTrue(server.campaigns().promise(caller(bob), "Die Umsatzsteuer beträgt 5 %.").success());
        assertEquals(2, server.campaigns().listPromises(caller(alice), Optional.empty()).lines().size());
        assertEquals(MessageKey.POSTER_PLACED.key(),
                server.campaigns().posterPlaced(caller(bob), HERE).lines().getFirst().key());
        assertEquals(MessageKey.POSTER_INFO.key(), server.campaigns().posterInfo(HERE).lines().getFirst().key());

        PollView poll = server.campaigns().polls(nordhain()).getFirst();
        assertEquals(3, poll.sample());
        assertEquals(MessageKey.POLL_HEADER.key(),
                server.campaigns().poll(caller(alice), Optional.empty()).lines().getFirst().key());

        assertTrue(server.offices().vote(caller(alice), "buergermeister", "Bob").success());
        server.endDayNow();
        server.endDayNow();
        server.endDayNow();
        ResultView result = server.campaigns().results(nordhain()).getFirst();
        assertEquals(List.of("Bob"), result.winners());
        assertEquals(MessageKey.RESULT_HEADER.key(),
                server.campaigns().lastResult(caller(alice), Optional.empty()).lines().getFirst().key());
        server.campaigns().posterRemoved(HERE);
        assertFalse(nordhain().posters().stream().anyMatch(poster -> poster.position().equals(HERE)));
    }

    private PolityView nordhain() {
        return server.queries().polityNamed("Nordhain").orElseThrow();
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", HERE);
    }
}
