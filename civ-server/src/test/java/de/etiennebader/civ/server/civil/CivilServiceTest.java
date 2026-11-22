package de.etiennebader.civ.server.civil;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.civil.CivilService.SignType;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.community.ChatView;
import de.etiennebader.civ.server.community.FriendView;
import de.etiennebader.civ.server.plot.PlotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.server.command.Message;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(73);
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));

    private static Caller at(PlayerRef player, String name, int x, int z) {
        return new Caller(player, name, new BlockPosition(DimensionId.OVERWORLD, x, 64, z));
    }

    private void setUpTown() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(at(alice, "Alice", 8, 8), "Nordhain").success());
        assertTrue(server.commands().join(at(bob, "Bob", 8, 8), "Nordhain").success());
    }

    private static boolean has(Reply reply, MessageKey key) {
        return reply.lines().stream().anyMatch(line -> line.key().equals(key.key()));
    }

    @Test
    @DisplayName("Straße anlegen, Grundstück vermieten, unterschreiben: der Mieter hat eine Anschrift")
    void streetLeaseAddress() {
        setUpTown();
        Caller mayor = at(alice, "Alice", 1, 1);
        assertTrue(has(server.civil().layStreet(mayor, "Hauptstraße"), MessageKey.STREET_NEEDS_SELECTION));
        server.plots().selectCorner(at(alice, "Alice", 0, 8), 1);
        server.plots().selectCorner(at(alice, "Alice", 15, 9), 2);
        assertTrue(server.civil().layStreet(mayor, "Hauptstraße").success());
        server.plots().selectCorner(at(alice, "Alice", 0, 0), 1);
        server.plots().selectCorner(at(alice, "Alice", 3, 3), 2);
        assertTrue(server.plots().createFromSelection(mayor, PlotService.typeNames().getFirst()).success());
        assertEquals(List.of("Hauptstraße"), server.civil().signLines(SignType.STREET, at(alice, "Alice", 2, 8).position()));
        assertEquals(List.of("Hauptstraße"), server.civil().signLines(SignType.STREET, mayor.position()));
        assertEquals(List.of("1"), server.civil().signLines(SignType.HOUSE, mayor.position()));
        assertEquals(List.of(), server.civil().signLines(SignType.HOUSE, at(alice, "Alice", 2, 8).position()));
        server.takeAnnouncements();

        Reply offered = server.civil().offerLease(mayor, "Bob", "1", Optional.of("2"), 0);
        assertTrue(offered.success(), offered.toString());
        List<Announcement> notices = server.takeAnnouncements();
        assertTrue(notices.stream().anyMatch(a -> a.recipient().equals(Optional.of(bob))
                && a.message().key().equals(MessageKey.CONTRACT_OFFERED_TO_YOU.key())), notices.toString());

        Caller tenant = at(bob, "Bob", 8, 8);
        assertFalse(server.civil().sign(tenant, 1).success(), "ohne Geld keine Kaution");
        server.bank().deposit(tenant, 10);
        assertTrue(server.civil().sign(tenant, 1).success());
        ContractView view = server.civil().contractsOf(bob).getFirst();
        assertTrue(view.pays() && view.canNotice() && view.address().orElseThrow().contains("Hauptstraße"));
        assertTrue(has(server.civil().address(tenant, Optional.empty()), MessageKey.ADDRESS_OF));
        assertTrue(has(server.civil().listStreets(mayor), MessageKey.STREET_ENTRY));
        assertTrue(server.civil().giveNotice(tenant, 1).success());
        server.civil().endOfDay(Day.of(10));
        assertTrue(server.takeAnnouncements().stream()
                .anyMatch(a -> a.message().key().equals(MessageKey.CONTRACT_ENDED.key())));
        assertFalse(server.civil().show(tenant, 99).success());
    }

    @Test
    @DisplayName("Personalausweis: nur für Mitglieder einer Gemeinde ausgestellt, nach Änderung der Daten veraltet")
    void idCardIssueAndValidity() {
        setUpTown();
        Caller mayor = at(alice, "Alice", 8, 8);
        PlayerRef stranger = new PlayerRef(PlayerId.generate(ids));
        server.playerJoined(stranger, "Stranger");
        Caller outside = at(stranger, "Stranger", 8, 8);
        assertFalse(server.civil().issueIdCard(outside, Optional.empty(), issue -> {
            throw new AssertionError("ohne Gemeinde darf nichts ausgestellt werden");
        }).success());

        java.util.concurrent.atomic.AtomicReference<CivilService.IdCardIssue> issued =
                new java.util.concurrent.atomic.AtomicReference<>();
        Reply reply = server.civil().issueIdCard(mayor, Optional.empty(), issued::set);
        assertTrue(has(reply, MessageKey.ID_CARD_ISSUED), reply.toString());
        assertEquals("Alice", issued.get().data().holderName());
        assertTrue(has(server.civil().checkIdCard(issued.get().holder(), issued.get().data()), MessageKey.ID_CARD_VALID));

        var stale = new de.etiennebader.civ.application.civil.IdCards.Data("Alice",
                Optional.of("Altstraße 9"), issued.get().data().citizenOf(), issued.get().data().issuedOn());
        assertTrue(has(server.civil().checkIdCard(issued.get().holder(), stale), MessageKey.ID_CARD_OUTDATED));
    }

    @Test
    @DisplayName("Ort, Land und Nation erscheinen abhängig vom Schildtyp und vom tatsächlichen Aufstellort")
    void territorialSignLines() {
        setUpTown();
        Caller mayor = at(alice, "Alice", 8, 8);
        assertEquals(List.of("Gemeinde: Nordhain"), server.civil().signLines(SignType.TOWN, mayor.position()));
        assertEquals(List.of(), server.civil().signLines(SignType.COUNTRY, mayor.position()));
        assertTrue(server.federations().found(mayor, "land", "Ost").success());
        assertTrue(server.federations().found(mayor, "nation", "Reich").success());
        assertEquals(List.of("Gemeinde: Nordhain", "Land: Ost", "Nation: Reich"),
                server.civil().signLines(SignType.TOWN, mayor.position()));
        assertEquals(List.of("Land: Ost", "Nation: Reich"), server.civil().signLines(SignType.COUNTRY, mayor.position()));
        assertEquals(List.of("Nation: Reich"), server.civil().signLines(SignType.NATION, mayor.position()));
        assertEquals(List.of(), server.civil().signLines(SignType.TOWN, at(alice, "Alice", 500, 500).position()));
    }

    @Test
    @DisplayName("Briefe, Freunde, Direktnachrichten und Chat zwischen Spiel und Webseite")
    void community() {
        setUpTown();
        Caller sender = at(alice, "Alice", 8, 8);
        assertTrue(server.community().sendLetter(sender, "Bob", "Einladung | Komm heute zum Rathaus.").success());
        assertFalse(server.community().sendLetter(sender, "Niemand", "x | y").success());
        assertTrue(server.community().inbox(bob).isEmpty(), "kommt erst morgen an");
        server.community().endOfDay(Day.of(2));

        assertFalse(server.community().direct(alice, "Alice", "Bob", "Hi").success(), "nur an Freunde");
        assertTrue(has(server.community().befriend(alice, "Alice", "Bob"), MessageKey.FRIEND_REQUEST_SENT));
        assertEquals("incoming", server.community().friends(bob).getFirst().state());
        assertTrue(has(server.community().befriend(bob, "Bob", "Alice"), MessageKey.FRIEND_ACCEPTED));
        FriendView friend = server.community().friends(alice).getFirst();
        assertEquals("friend", friend.state());
        assertTrue(friend.online());

        server.takeAnnouncements();
        assertTrue(server.community().direct(alice, "Alice", "Bob", "Hi Bob").success());
        server.community().gameChat(bob, "Hallo zusammen");
        ChatView web = server.community().webPost(alice, Optional.empty(), "Gruß von der Webseite");
        assertTrue(web.mine());
        assertTrue(server.takeAnnouncements().stream().anyMatch(a -> a.message().key().equals(MessageKey.WEB_CHAT.key())));
        assertEquals(3, server.community().chat(bob, 0).size());
        assertEquals(1, server.community().chat(bob, web.seq() - 1).size());

        server.community().left(bob);
        assertFalse(server.community().friends(alice).getFirst().online());
        assertTrue(server.community().unfriend(alice, "Bob").success());
    }

    @Test
    @DisplayName("Freundschaft: ablehnen, Knöpfe im Chat, Baurecht für Freunde auf dem gemieteten Grundstück")
    void friendTrust() {
        setUpTown();
        PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
        server.playerJoined(carla, "Carla");
        Caller mayor = at(alice, "Alice", 1, 1);
        server.plots().selectCorner(at(alice, "Alice", 0, 0), 1);
        server.plots().selectCorner(at(alice, "Alice", 3, 3), 2);
        assertTrue(server.plots().createFromSelection(mayor, PlotService.typeNames().getFirst()).success());
        assertTrue(server.civil().offerLease(mayor, "Bob", "1", Optional.empty(), 0).success());
        Caller tenant = at(bob, "Bob", 8, 8);
        server.bank().deposit(tenant, 10);
        assertTrue(server.civil().sign(tenant, 1).success());
        BlockPosition inPlot = new BlockPosition(DimensionId.OVERWORLD, 2, 64, 2);
        assertTrue(server.queries().access(carla, ProtectedAction.BUILD, inPlot).isPresent());

        server.takeAnnouncements();
        assertTrue(server.community().befriend(carla, "Carla", "Bob").success());
        assertTrue(server.takeAnnouncements().stream().anyMatch(a -> a.message().key()
                .equals(MessageKey.FRIEND_REQUEST_ACTIONS.key()) && a.message().args().getFirst() instanceof Message button
                && button.isAction()));
        assertTrue(has(server.community().decline(bob, "Carla"), MessageKey.FRIEND_DECLINED));
        assertFalse(server.community().decline(bob, "Carla").success(), "keine Anfrage mehr");
        assertFalse(server.community().trust(bob, "Carla", true).success(), "nur Freunden");

        server.community().befriend(carla, "Carla", "Bob");
        server.community().befriend(bob, "Bob", "Carla");
        assertTrue(has(server.community().trust(bob, "Carla", true), MessageKey.FRIEND_TRUSTED));
        assertTrue(server.community().friends(bob).getFirst().trusted());
        assertTrue(server.community().friends(carla).getFirst().trustsMe());
        assertTrue(server.queries().access(carla, ProtectedAction.BUILD, inPlot).isEmpty(), "Freund darf bauen");
        assertTrue(server.community().unfriend(carla, "Bob").success());
        assertTrue(server.queries().access(carla, ProtectedAction.BUILD, inPlot).isPresent(), "Freundschaft beendet");
    }
}
