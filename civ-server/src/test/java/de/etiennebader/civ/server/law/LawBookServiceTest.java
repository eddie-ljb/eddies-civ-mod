package de.etiennebader.civ.server.law;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.town.LawSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawBookServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(71);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Erlassen, lesen, prüfen, aufheben; mit Abstimmungspflicht wird ein Antrag daraus")
    void lifecycle() {
        town();
        Reply enacted = server.lawBooks().enact(caller(alice), "Hausordnung",
                "Explosionen sind verboten. Fremde dürfen bauen. Wer stiehlt, wird verbannt.");
        assertEquals(MessageKey.PARAGRAPH_ENACTED.key(), enacted.lines().getFirst().key());
        assertEquals(MessageKey.PARAGRAPH_UNENFORCED.key(), enacted.lines().getLast().key());
        assertTrue(named("Nordhain").laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));

        List<ParagraphView> paragraphs = server.lawBooks().paragraphsOf(named("Nordhain"));
        assertEquals(1, paragraphs.size());
        assertEquals(2, paragraphs.getFirst().effects().size());
        assertEquals(List.of("Wer stiehlt", "wird verbannt."), paragraphs.getFirst().unrecognized());
        assertTrue(server.lawBooks().show(caller(bob), 1).success());
        assertFalse(server.lawBooks().show(caller(bob), 2).success());
        assertEquals(4, server.lawBooks().check("Umsatzsteuer 5 %. Keine Außenposten. Hallo.").lines().size());
        assertFalse(server.lawBooks().enact(caller(bob), "Bobs Gesetz", "Feuer ist erlaubt.").success());

        assertTrue(server.lawBooks().repeal(caller(alice), 1).success());
        assertTrue(server.lawBooks().paragraphsOf(named("Nordhain")).isEmpty());

        server.commands().amendLaw(caller(alice), "laws_by_vote", "ja");
        Reply proposed = server.lawBooks().enact(caller(bob), "Bobs Gesetz", "Feuer ist erlaubt.");
        assertEquals(MessageKey.PARAGRAPH_PROPOSED.key(), proposed.lines().getFirst().key());
        assertEquals(1, named("Nordhain").proposals().size());
        assertTrue(server.voting().vote(caller(alice), 1, true).success());
        assertTrue(named("Nordhain").laws().valueOf(StandardParameters.FIRE_SPREAD_ALLOWED));
    }

    @Test
    @DisplayName("Bundesrecht zeigt sich in der Rechtsübersicht der Gemeinde")
    void federalLawShown() {
        town();
        server.federations().found(caller(alice), "land", "Westmark");
        assertTrue(server.lawBooks().enact(caller(alice), "Landesfrieden", "Fremde dürfen nicht bauen.").success(),
                "Alice führt das Land und erlässt dort");
        assertTrue(named("Westmark").laws().inForce().size() == 1);
        server.commands().amendLaw(caller(alice), "outsiders_may_build", "ja");
        LawSetting build = server.towns().laws(server.towns().overview(named("Nordhain"), alice)).stream()
                .filter(law -> law.id().equals("outsiders_may_build")).findFirst().orElseThrow();
        assertEquals(false, build.value());
        assertEquals(true, build.own());
        assertEquals(Optional.of("Westmark"), build.federal());
        assertTrue(server.lawBooks().list(caller(bob), Optional.empty()).lines().stream()
                .anyMatch(line -> line.key().equals(MessageKey.LAW_BOOK_FEDERAL.key())));
    }

    private void town() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
    }

    private PolityView named(String name) {
        return server.queries().polityNamed(name).orElseThrow();
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}
