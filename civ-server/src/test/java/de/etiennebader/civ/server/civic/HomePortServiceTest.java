package de.etiennebader.civ.server.civic;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.civic.PortAccess;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomePortServiceTest {

    private static final BlockPosition HOME = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);
    private static final BlockPosition FAR = new BlockPosition(DimensionId.OVERWORLD, 5_000, 64, 5_000);

    private final SeededIdGenerator ids = new SeededIdGenerator(51);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef eve = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    private void setUp() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(eve, "Eve");
        assertTrue(server.commands().found(caller(alice, HOME), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob, HOME), "Nordhain").success());
    }

    @Test
    @DisplayName("Standard: Bürger dürfen zum Gemeindehaus, Fremde nicht; der Bürgermeister immer")
    void defaultAccess() {
        setUp();
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        HomePortService.Outcome citizen = server.homePorts().prepare(caller(bob, FAR), Optional.empty());
        assertTrue(citizen.trip().isPresent(), "Bob ist Bürger");
        assertEquals(town.seat().orElseThrow(), citizen.trip().get().target());
        assertEquals(0, citizen.trip().get().fee());

        HomePortService.Outcome stranger = server.homePorts().prepare(caller(eve, FAR), Optional.of("Nordhain"));
        assertTrue(stranger.trip().isEmpty());
        assertEquals(MessageKey.PORT_NOT_ALLOWED.key(), stranger.refusal().orElseThrow().lines().getFirst().key());
    }

    @Test
    @DisplayName("Zugangsregeln: keiner sperrt Bürger aus, alle öffnet für Fremde; nur die Spitze darf ändern")
    void accessRules() {
        setUp();
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertFalse(server.homePorts().setAccess(caller(bob, HOME), town, "alle").success(), "Bob ist nicht Bürgermeister");
        assertFalse(server.homePorts().setAccess(caller(alice, HOME), town, "quatsch").success());

        assertTrue(server.homePorts().setAccess(caller(alice, HOME), town, "alle").success());
        assertTrue(server.homePorts().prepare(caller(eve, FAR), Optional.of("Nordhain")).trip().isPresent());

        assertTrue(server.homePorts().setAccess(caller(alice, HOME), town, "keiner").success());
        assertTrue(server.homePorts().prepare(caller(bob, FAR), Optional.empty()).trip().isEmpty());
        assertTrue(server.homePorts().prepare(caller(alice, FAR), Optional.empty()).trip().isPresent(),
                "die Spitze darf immer");
        assertEquals(PortAccess.NOBODY, server.queries().civic().portSettingsOf(town.id()).access());
    }

    @Test
    @DisplayName("Zusätzliche Häfen: nur Slot 2 und 3, nur auf eigenem Gebiet, wählbar per Slotnummer")
    void extraPorts() {
        setUp();
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertFalse(server.homePorts().set(caller(alice, HOME), town, 1).success());
        assertFalse(server.homePorts().set(caller(alice, HOME), town, 4).success());
        Reply outside = server.homePorts().set(caller(alice, FAR), town, 2);
        assertFalse(outside.success());
        assertEquals(MessageKey.PORT_OUTSIDE_TERRITORY.key(), outside.lines().getFirst().key());
        assertFalse(server.homePorts().set(caller(bob, HOME), town, 2).success(), "nur die Spitze");

        BlockPosition market = new BlockPosition(DimensionId.OVERWORLD, 12, 70, 3);
        assertTrue(server.homePorts().set(caller(alice, market), town, 2).success());
        assertEquals(market, server.homePorts().prepare(caller(bob, FAR), Optional.of("2")).trip().orElseThrow().target());
        assertEquals(market, server.homePorts().prepare(caller(bob, FAR), Optional.of("Nordhain 2")).trip().orElseThrow()
                .target());
        assertTrue(server.homePorts().prepare(caller(bob, FAR), Optional.of("3")).trip().isEmpty(), "Slot 3 ist frei");

        assertTrue(server.homePorts().remove(caller(alice, HOME), town, 2).success());
        assertFalse(server.homePorts().remove(caller(alice, HOME), town, 2).success());
        assertTrue(server.homePorts().prepare(caller(bob, FAR), Optional.of("2")).trip().isEmpty());
    }

    @Test
    @DisplayName("Preis: Bürger zahlen, die Spitze nicht; ohne Geld wird nichts gebucht")
    void fee() {
        setUp();
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertFalse(server.homePorts().setFee(caller(alice, HOME), town, "abc").success());
        assertTrue(server.homePorts().setFee(caller(alice, HOME), town, "5").success());

        HomePortService.Trip trip = server.homePorts().prepare(caller(bob, FAR), Optional.empty()).trip().orElseThrow();
        assertEquals(500, trip.fee());
        assertEquals(0, server.homePorts().prepare(caller(alice, FAR), Optional.empty()).trip().orElseThrow().fee());
        assertFalse(server.homePorts().pay(caller(bob, FAR), trip).success(), "Bob hat kein Geld");

        assertTrue(server.homePorts().setFee(caller(alice, HOME), town, "frei").success());
        assertEquals(0, server.homePorts().prepare(caller(bob, FAR), Optional.empty()).trip().orElseThrow().fee());
    }

    @Test
    @DisplayName("Liste zeigt Gemeindehaus und freie Slots")
    void list() {
        setUp();
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        Reply list = server.homePorts().list(town);
        assertTrue(list.success());
        assertEquals(4, list.lines().size(), "Kopf, Gemeindehaus, Slot 2, Slot 3");
        assertEquals(MessageKey.PORT_ENTRY_SEAT.key(), list.lines().get(1).key());
    }

    @Test
    @DisplayName("Wartezeit: nach einem Teleport gilt sie, 0 im Recht hebt sie auf")
    void cooldown() {
        setUp();
        HomePortService.Trip trip = server.homePorts().prepare(caller(bob, FAR), Optional.empty()).trip().orElseThrow();
        assertTrue(server.homePorts().pay(caller(bob, FAR), trip).success());

        HomePortService.Outcome again = server.homePorts().prepare(caller(bob, FAR), Optional.empty());
        assertTrue(again.trip().isEmpty());
        assertEquals(MessageKey.PORT_COOLDOWN.key(), again.refusal().orElseThrow().lines().getFirst().key());
        assertTrue(server.homePorts().prepare(caller(alice, FAR), Optional.empty()).trip().isPresent(),
                "die Wartezeit gilt je Spieler");

        assertTrue(server.levels().amendLaw(caller(alice, HOME), "gemeinde", "home_port_cooldown_seconds", "0").success());
        assertTrue(server.homePorts().prepare(caller(bob, FAR), Optional.empty()).trip().isPresent());
    }

    @Test
    @DisplayName("Krieg: Häfen einer Gemeinde im Krieg sind für Fremde (auch bei Zugang alle) gesperrt, Bürger dürfen")
    void war() {
        setUp();
        PolityView town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertTrue(server.homePorts().setAccess(caller(alice, HOME), town, "alle").success());
        assertTrue(server.commands().found(caller(eve, FAR), "Südtal").success());
        PolityView other = server.queries().polityNamed("Südtal").orElseThrow();
        assertTrue(server.homePorts().prepare(caller(eve, FAR), Optional.of("Nordhain")).trip().isPresent());

        server.war().registry().declareWar(ids, other.id(), town.id(), de.etiennebader.civ.domain.shared.Day.of(0));
        HomePortService.Outcome blocked = server.homePorts().prepare(caller(eve, FAR), Optional.of("Nordhain"));
        assertTrue(blocked.trip().isEmpty());
        assertEquals(MessageKey.PORT_WAR.key(), blocked.refusal().orElseThrow().lines().getFirst().key());
        assertTrue(server.homePorts().prepare(caller(bob, FAR), Optional.empty()).trip().isPresent(),
                "Bürger dürfen weiter heim");
    }

    @Test
    @DisplayName("war_port_lock=enemies: nur Kriegsgegner sind gesperrt, Unbeteiligte und Verbündete nicht")
    void warLockEnemiesOnly() {
        ServerSettings base = ServerSettings.defaults();
        CivServerFixture lenient = CivServerFixture.start(ids, new ServerSettings(base.ticksPerDay(), base.claimBase(),
                base.claimPerMember(), base.currency(), base.priceReference(), base.villagerIncome(), base.tiers(),
                base.web(), base.emigration(), base.villagerPolitics(), base.levels(), base.parties(), base.show(),
                base.economy(), base.ambitions(), ServerSettings.WarPortLock.ENEMIES));
        PlayerRef carol = new PlayerRef(PlayerId.generate(ids));
        for (PlayerRef player : new PlayerRef[]{alice, eve, carol}) {
            lenient.playerJoined(player, "P");
        }
        assertTrue(lenient.commands().found(caller(alice, HOME), "Nordhain").success());
        assertTrue(lenient.commands().found(caller(eve, FAR), "Südtal").success());
        BlockPosition third = new BlockPosition(DimensionId.OVERWORLD, -5_000, 64, -5_000);
        assertTrue(lenient.commands().found(caller(carol, third), "Ostburg").success());
        PolityView north = lenient.queries().polityNamed("Nordhain").orElseThrow();
        PolityView south = lenient.queries().polityNamed("Südtal").orElseThrow();
        assertTrue(lenient.homePorts().setAccess(caller(alice, HOME), north, "alle").success());

        lenient.war().registry().declareWar(ids, south.id(), north.id(), de.etiennebader.civ.domain.shared.Day.of(0));
        assertTrue(lenient.homePorts().prepare(caller(eve, FAR), Optional.of("Nordhain")).trip().isEmpty(),
                "Südtal ist Kriegsgegner");
        assertTrue(lenient.homePorts().prepare(caller(carol, third), Optional.of("Nordhain")).trip().isPresent(),
                "Ostburg ist unbeteiligt");
    }

    private static Caller caller(PlayerRef player, BlockPosition position) {
        return new Caller(player, "Spieler", position);
    }
}
