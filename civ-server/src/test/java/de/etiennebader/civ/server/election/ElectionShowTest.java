package de.etiennebader.civ.server.election;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectionShowTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(91);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Live-Auszählung: Zwischenstände mit Hochrechnung, am Ende das amtliche Ergebnis")
    void liveCount() {
        Announcements announcements = new Announcements();
        LiveCountService live = new LiveCountService(announcements, true, 60, true);
        PolityId town = new PolityId(UUID.randomUUID());
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("Alice", 600L);
        totals.put("Bob", 400L);
        Message finale = Message.of(MessageKey.ELECTION_WON, "Nordhain", "Amt", "Alice", "6, 4");
        live.start(town, "Nordhain", Message.of(MessageKey.OFFICES_HEADER, "x"), totals, Map.of(), List.of(finale), 0);
        assertEquals(MessageKey.LIVE_COUNT_STARTED.key(), announcements.take().getFirst().message().key());
        live.tick(15_000);
        List<Announcement> step = announcements.take();
        assertTrue(step.stream().anyMatch(a -> a.message().key().equals(MessageKey.LIVE_COUNT_STEP.key())));
        assertTrue(step.stream().anyMatch(a -> a.message().key().equals(MessageKey.LIVE_COUNT_PROJECTION.key())));
        assertEquals(1, live.running().size());
        live.tick(60_000);
        assertEquals(finale, announcements.take().getLast().message());
        assertTrue(live.running().isEmpty());
        assertEquals(60.0, live.recent().getLast().shares().get("Alice"), 0.01);

        LiveCountService off = new LiveCountService(announcements, false, 60, true);
        off.start(town, "Nordhain", Message.of(MessageKey.OFFICES_HEADER, "x"), totals, Map.of(), List.of(finale), 0);
        assertEquals(finale, announcements.take().getFirst().message(), "abgeschaltet: sofort das Ergebnis");
    }

    @Test
    @DisplayName("Wahlkreise eines Bundes über ganze Gemeinden; Debatte mit Beiträgen und Blitzumfrage")
    void constituenciesAndDebate() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(carla, "Carla");
        assertTrue(server.commands().found(caller(alice, 8), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob, 8), "Nordhain").success());
        assertTrue(server.commands().found(caller(carla, 800), "Eichenfurt").success());
        assertTrue(server.federations().found(caller(alice, 8), "land", "Westmark").success());
        server.federations().invite(caller(alice, 8), "Eichenfurt");
        assertTrue(server.federations().join(caller(carla, 800), "Westmark").success());
        PolityView land = server.queries().polityNamed("Westmark").orElseThrow();

        assertTrue(server.levels().constituency(caller(alice, 8), "land", "anlegen", "Nord").success());
        assertTrue(server.levels().constituency(caller(alice, 8), "land", "anlegen", "Süd").success());
        assertFalse(server.levels().constituency(caller(bob, 8), "land", "anlegen", "Mitte").success(), "nur die Spitze");
        assertTrue(server.levels().constituencyTown(caller(alice, 8), "land", "Nord", "Nordhain").success());
        assertTrue(server.levels().constituencyTown(caller(alice, 8), "land", "Süd", "Eichenfurt").success());
        Map<PolityId, Long> voters = server.civic().votersPerDistrict(server.queries().polityNamed("Westmark").orElseThrow());
        PolityId north = server.queries().civic().constituencyNamed(land.id(), "Nord").orElseThrow().id().asDistrict();
        PolityId south = server.queries().civic().constituencyNamed(land.id(), "Süd").orElseThrow().id().asDistrict();
        assertTrue(voters.getOrDefault(north, 0L) >= 2, voters.toString());
        assertTrue(voters.getOrDefault(south, 0L) >= 1, voters.toString());
        Reply list = server.levels().constituencies(caller(alice, 8), "land");
        assertEquals(MessageKey.CONSTITUENCY_HEADER.key(), list.lines().getFirst().key());

        assertTrue(server.levels().callElection(caller(alice, 8), "land", "schatzmeister").success());
        assertTrue(server.levels().stand(caller(alice, 8), "land", "schatzmeister",
                java.util.Optional.of("umwelt")).success());
        assertTrue(server.levels().stand(caller(carla, 800), "land", "schatzmeister",
                java.util.Optional.of("wirtschaft")).success());
        assertTrue(server.levels().startDebate(caller(alice, 8), "land", java.util.Optional.empty()).success());
        server.takeAnnouncements();
        assertTrue(server.debates().speak(caller(alice, 8), "Ich schütze Wald und Tiere!", System.currentTimeMillis())
                .success());
        assertFalse(server.debates().speak(caller(bob, 8), "Hallo", System.currentTimeMillis()).success(),
                "nur Kandidaten");
        long later = System.currentTimeMillis() + 6 * 60_000;
        server.debates().tick(later);
        List<Announcement> said = server.takeAnnouncements();
        assertTrue(said.stream().anyMatch(a -> a.message().key().equals(MessageKey.DEBATE_LINE.key())
                && a.message().args().get(1).equals("Carla")), "Carlas Team spricht für sie");
        assertTrue(said.stream().anyMatch(a -> a.message().key().equals(MessageKey.DEBATE_ENDED.key())));
        assertTrue(server.debates().debates(later).getFirst().finished());
    }

    private static Caller caller(PlayerRef player, int x) {
        return new Caller(player, "?", new BlockPosition(DimensionId.OVERWORLD, x, 64, x));
    }
}
