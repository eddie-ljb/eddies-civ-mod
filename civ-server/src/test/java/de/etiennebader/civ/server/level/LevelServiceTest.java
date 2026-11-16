package de.etiennebader.civ.server.level;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
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

class LevelServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(77);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Befehle je Ebene: Nation-Parlament einrichten, Wahl ausrufen, kandidieren, wählen")
    void nationParliament() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());

        assertEquals(MessageKey.LEVEL_NOT_JOINED.key(), firstKey(server.levels().offices(caller(alice), "nation")));
        assertEquals(MessageKey.UNKNOWN_LEVEL.key(), firstKey(server.levels().offices(caller(alice), "kaiserreich")));

        assertTrue(server.federations().found(caller(alice), "land", "Westmark").success());
        assertTrue(server.federations().found(caller(alice), "nation", "Reich").success());
        PolityView nation = named("Reich");
        assertEquals(List.of("Nordhain", "Westmark", "Reich"),
                server.levels().chainOf(alice).stream().map(polity -> polity.name().value()).toList());
        assertEquals(Optional.of(nation.id()), server.levels().levels().stream()
                .filter(level -> level.id().equals("nation")).findFirst()
                .flatMap(level -> server.levels().polityOf(bob, level)).map(PolityView::id));

        assertTrue(firstKey(server.levels().callElection(caller(alice), "nation", "parlament"))
                        .endsWith("no_parliament_provided"),
                "ohne Sitze im Recht der Nation gibt es keine Parlamentswahl");
        assertTrue(server.levels().amendLaw(caller(alice), "nation", "parliament_seats", "5").success());
        assertEquals(5L, named("Reich").laws().valueOf(StandardParameters.PARLIAMENT_SEATS));
        assertEquals(0L, named("Nordhain").laws().valueOf(StandardParameters.PARLIAMENT_SEATS),
                "das Recht der Gemeinde bleibt unberührt");

        assertTrue(server.levels().callElection(caller(alice), "nation", "parlament").success());
        assertTrue(named("Reich").electionFor(Office.PARLIAMENT).isPresent());
        assertTrue(named("Nordhain").electionFor(Office.PARLIAMENT).isEmpty());
        assertTrue(server.levels().stand(caller(bob), "nation", "parlament", Optional.of("ordnung")).success());
        assertTrue(server.levels().vote(caller(alice), "nation", "parlament", "Bob").success());
        assertFalse(server.levels().vote(caller(alice), "nation", "parlament", "Niemand").success());

        Reply offices = server.levels().offices(caller(alice), "nation");
        assertTrue(offices.success());
        assertEquals(MessageKey.OFFICES_HEADER.key(), firstKey(offices));
        assertTrue(server.levels().parliament(caller(alice), "nation").success());
        assertTrue(server.levels().laws(caller(alice), "land").success());
        assertTrue(server.levels().info(caller(alice), "land").success());
        assertTrue(server.levels().info(caller(alice), "gemeinde").success());
        assertTrue(server.levels().proposals(caller(alice), "nation").success());
        assertTrue(server.levels().poll(caller(alice), "nation").success());

        Reply overview = server.levels().overview(caller(bob));
        assertEquals(MessageKey.LEVEL_CHAIN_HEADER.key(), firstKey(overview));
        assertEquals(5, overview.lines().size(), "Kopf, Gemeinde, Land, Nation, Hinweis");
    }

    private PolityView named(String name) {
        return server.queries().polityNamed(name).orElseThrow();
    }

    private static String firstKey(Reply reply) {
        return reply.lines().getFirst().key();
    }

    private Caller caller(PlayerRef player) {
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob",
                new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}
