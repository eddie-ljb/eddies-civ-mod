package de.etiennebader.civ.server.company;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.press.PressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAndPressServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(91);
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final Caller aliceAt = new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    private final Caller bobAt = new Caller(bob, "Bob", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));

    private static boolean has(Reply reply, MessageKey key) {
        return reply.lines().stream().anyMatch(line -> line.key().equals(key.key()));
    }

    private void setUp() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(aliceAt, "Nordhain").success());
        assertTrue(server.commands().join(bobAt, "Nordhain").success());
        server.bank().deposit(aliceAt, 30);
        server.bank().deposit(bobAt, 10);
    }

    @Test
    @DisplayName("Firma gründen, Aktien handeln, Depot, Orderbuch, Dividende mit Nachricht an Aktionäre")
    void company() {
        setUp();
        assertTrue(has(server.companies().found(aliceAt, "5", "Klein AG"), MessageKey.INVALID_AMOUNT)
                || !server.companies().found(aliceAt, "5", "Klein AG").success());
        assertTrue(server.companies().found(aliceAt, "20", "Holz AG").success());
        assertEquals(1, server.companies().listings().size());
        assertEquals("0,02", server.companies().listings().getFirst().price());

        assertTrue(has(server.companies().trade(aliceAt, Side.SELL, "Holz AG", 300, Optional.of("0,03")),
                MessageKey.ORDER_RESTING));
        assertTrue(has(server.companies().book("Holz AG"), MessageKey.BOOK_ASK));
        server.takeAnnouncements();
        Reply bought = server.companies().trade(bobAt, Side.BUY, "Holz AG", 100, Optional.empty());
        assertTrue(has(bought, MessageKey.ORDER_FILLED), bought.toString());
        assertTrue(server.takeAnnouncements().stream().anyMatch(a -> a.recipient().equals(Optional.of(alice))
                && a.message().key().equals(MessageKey.ORDER_EXECUTED_FOR_YOU.key())));
        assertEquals(100L, server.companies().holdingsOf(bob).getFirst().shares());
        assertTrue(has(server.companies().depot(bobAt), MessageKey.DEPOT_ENTRY));
        assertEquals("0,03", server.companies().listings().getFirst().price());

        assertFalse(server.companies().dividend(bobAt, "Holz AG", "10").success());
        assertTrue(server.companies().dividend(aliceAt, "Holz AG", "10").success());
        assertTrue(server.takeAnnouncements().stream().anyMatch(a -> a.recipient().equals(Optional.of(bob))
                && a.message().key().equals(MessageKey.DIVIDEND_PAID.key())));
        assertTrue(has(server.companies().info(aliceAt, Optional.empty()), MessageKey.COMPANY_HOLDER));

        int order = server.companies().ordersOf(alice).getFirst().number();
        assertTrue(server.companies().cancel(aliceAt, order).success());
        assertFalse(server.companies().cancel(bobAt, order).success());
        server.companies().trade(aliceAt, Side.SELL, "Holz AG", 10, Optional.of("1"));
        server.companies().endOfDay(Day.of(30));
        assertTrue(server.takeAnnouncements().stream()
                .anyMatch(a -> a.message().key().equals(MessageKey.ORDER_EXPIRED.key())));
    }

    @Test
    @DisplayName("Zeitung gründen, Redakteur aufnehmen, Artikel schreiben: Schlagzeile in der Gemeinde, Ausgabe als Buch")
    void press() {
        setUp();
        assertTrue(server.press().found(aliceAt, "Nordhainer Bote").success());
        assertFalse(server.press().write(bobAt, "Nordhainer Bote", "Hallo | Welt").success());
        assertTrue(server.press().addEditor(aliceAt, "Nordhainer Bote", "Bob").success());
        server.takeAnnouncements();
        Reply published = server.press().write(bobAt, "Nordhainer Bote",
                "Alice ist großartig | Alice ist ein Vorbild und sehr erfolgreich.");
        assertTrue(has(published, MessageKey.ARTICLE_PUBLISHED), published.toString());
        assertTrue(published.lines().stream().anyMatch(line -> line.key().equals(MessageKey.ARTICLE_ANALYSIS.key())
                && line.args().contains("Alice") && line.args().contains("lobend")), published.toString());
        List<Announcement> news = server.takeAnnouncements();
        assertTrue(news.stream().anyMatch(a -> a.town().isPresent()
                && a.message().key().equals(MessageKey.PRESS_HEADLINE.key())), news.toString());
        assertEquals(1, server.press().newspapers().getFirst().articles());
        assertEquals(List.of("Nordhain"), server.press().newspapers().getFirst().towns());
        PressService.Edition edition = server.press().edition(Optional.of("Nordhainer Bote")).orElseThrow();
        assertEquals(2, edition.pages().size());
        assertTrue(edition.pages().get(1).contains("Alice ist großartig"));
        assertTrue(has(server.press().show(1), MessageKey.ARTICLE_HEADER));
        assertTrue(has(server.press().list(), MessageKey.NEWSPAPER_ENTRY));
        assertTrue(server.press().dissolve(aliceAt, "Nordhainer Bote").success());
        assertTrue(server.press().latest(10).isEmpty());
    }
}
