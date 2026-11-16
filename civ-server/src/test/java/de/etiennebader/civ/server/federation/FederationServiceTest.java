package de.etiennebader.civ.server.federation;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(51);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids, withLevels("kreis:Kreis:10,bund:Bund:40"));

    @Test
    @DisplayName("Ebenen: eingebaute plus eigene aus der Konfiguration, nach Rang; fehlerhafte Einträge fallen weg")
    void levels() {
        Levels levels = Levels.parse("kreis:Kreis:10, bund:Bund:40, kaputt, land:Doppelt:5, x:Y:abc");
        assertEquals(List.of("gemeinde", "kreis", "land", "nation", "bund"),
                levels.all().stream().map(Level::id).toList());
        assertEquals("kreis:Kreis:10,bund:Bund:40", levels.spec());
        assertEquals(Optional.of("bund"), levels.find("Bund").map(Level::id));
        assertTrue(levels.of("land").builtIn());
        assertEquals(Levels.UNKNOWN_RANK, levels.of("verschwunden").rank());
    }

    @Test
    @DisplayName("Kreis gründen, Gemeinde einladen und aufnehmen, darüber ein Land; Ränge werden geprüft")
    void foundInviteJoin() {
        towns();

        assertEquals(MessageKey.UNKNOWN_LEVEL.key(), firstKey(server.federations().found(caller(alice), "kaiserreich", "X")));
        assertEquals(MessageKey.UNKNOWN_LEVEL.key(), firstKey(server.federations().found(caller(alice), "gemeinde", "X")));
        assertEquals(MessageKey.NOT_A_HEAD.key(), firstKey(server.federations().found(caller(bob), "kreis", "X")));
        assertTrue(server.federations().found(caller(alice), "kreis", "Nordkreis").success());
        PolityView kreis = named("Nordkreis");
        assertEquals(PolityKind.FEDERATION, kreis.kind());
        assertEquals("kreis", kreis.level());
        assertTrue(kreis.isMember(bob));

        assertFalse(server.federations().join(caller(carla), "Nordkreis").success(), "nur auf Einladung");
        assertTrue(server.federations().invite(caller(alice), "Eichenfurt").success());
        assertTrue(server.takeAnnouncements().stream().anyMatch(a -> a.town().equals(Optional.of(named("Eichenfurt").id()))
                && a.message().key().equals(MessageKey.FEDERATION_INVITATION.key())));
        assertTrue(server.federations().join(caller(carla), "Nordkreis").success());
        assertTrue(named("Nordkreis").isMember(carla));

        assertEquals(MessageKey.LEVEL_TOO_LOW.key(), firstKey(server.federations().found(caller(alice), "kreis", "Y")),
                "Alice führt jetzt den Kreis; ein weiterer Kreis darüber steht nicht höher");
        assertTrue(server.federations().found(caller(alice), "land", "Westmark").success());
        assertEquals(Optional.of(named("Westmark")), server.queries().parentOf(kreis.id()));
        assertTrue(named("Westmark").isMember(carla));

        Message belongsTo = server.federations().belongsTo(named("Eichenfurt")).orElseThrow();
        assertEquals(MessageKey.FEDERATION_BELONGS_TO.key(), belongsTo.key());
        assertEquals(MessageKey.FEDERATION_CHAIN.key(), ((Message) belongsTo.args().getFirst()).key());
        assertTrue(server.federations().info(caller(carla), Optional.empty()).lines().size() >= 8);
        assertTrue(server.federations().info(caller(carla), Optional.of("Westmark")).success());
        assertTrue(server.federations().listLevels().lines().size() == 6);
    }

    @Test
    @DisplayName("Austreten, Auflösen und kein Beitritt einzelner Spieler zu einem Bund")
    void leaveAndDissolve() {
        towns();
        server.federations().found(caller(alice), "land", "Westmark");
        server.federations().invite(caller(alice), "Eichenfurt");
        server.federations().join(caller(carla), "Westmark");

        assertFalse(server.commands().join(caller(bob), "Westmark").success());
        assertTrue(server.federations().leave(caller(carla)).success());
        assertFalse(named("Westmark").isMember(carla));
        assertEquals(MessageKey.FEDERATION_NONE.key(),
                firstKey(server.federations().info(caller(carla), Optional.empty())));

        assertEquals(MessageKey.NOT_HEAD_OF_FEDERATION.key(), firstKey(server.federations().dissolve(caller(carla))));
        server.takeAnnouncements();
        assertTrue(server.federations().dissolve(caller(alice)).success());
        assertTrue(server.queries().polityNamed("Westmark").isEmpty());
        List<Announcement> announced = server.takeAnnouncements();
        assertTrue(announced.stream().anyMatch(a -> a.message().key().equals(MessageKey.FEDERATION_DISSOLVED.key())));
        assertTrue(server.queries().polityNamed("Nordhain").isPresent());
    }

    private void towns() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(carla, "Carla");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
        assertTrue(server.commands().found(caller(carla), "Eichenfurt").success());
    }

    private PolityView named(String name) {
        return server.queries().polityNamed(name).orElseThrow();
    }

    private static String firstKey(Reply reply) {
        return reply.lines().getFirst().key();
    }

    private Caller caller(PlayerRef player) {
        if (player.equals(carla)) {
            return new Caller(player, "Carla", new BlockPosition(DimensionId.OVERWORLD, 800, 64, 800));
        }
        return new Caller(player, player.equals(alice) ? "Alice" : "Bob", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }

    private static ServerSettings withLevels(String spec) {
        ServerSettings base = CivServerFixture.SETTINGS;
        return new ServerSettings(base.ticksPerDay(), base.claimBase(), base.claimPerMember(), base.currency(),
                base.priceReference(), base.villagerIncome(), base.tiers(), base.web(), base.emigration(),
                Levels.parse(spec));
    }
}
