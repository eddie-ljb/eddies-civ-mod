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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    @DisplayName("Rechte-Editor: ausdrückliche Werte werden Paragraph; Fremde und ungültige Werte scheitern")
    void rightsEditor() {
        town();
        var polity = named("Nordhain");
        assertTrue(server.lawBooks().canLegislate(caller(alice), polity.id()));
        assertTrue(server.lawBooks().canLegislate(caller(bob), polity.id()), "Bürger sind Gesetzgeber der Gemeinde");
        assertFalse(server.lawBooks().enactRights(caller(alice), polity.id(), "Leer", java.util.Map.of()).success());
        assertFalse(server.lawBooks().enactRights(caller(alice), polity.id(), "Falsch",
                java.util.Map.of("outsiders_may_build", "vielleicht")).success());
        assertFalse(server.lawBooks().enactRights(caller(alice), polity.id(), "Unbekannt",
                java.util.Map.of("gibt_es_nicht", "1")).success());
        assertTrue(server.lawBooks().enactRights(caller(alice), polity.id(), "Offene Stadt",
                java.util.Map.of("outsiders_may_build", "true")).success());
        assertTrue(named("Nordhain").laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        ParagraphView paragraph = server.lawBooks().paragraphsOf(named("Nordhain")).getFirst();
        assertEquals("", paragraph.text());
        assertEquals(1, paragraph.effects().size());
        assertTrue(server.lawBooks().rightsOf(named("Nordhain")).stream()
                .anyMatch(right -> right.key().id().equals("outsiders_may_build") && right.explicit()));
        assertFalse(server.lawBooks().canLegislate(caller(alice), new de.etiennebader.civ.domain.shared.PolityId(
                java.util.UUID.randomUUID())));
    }

    @Test
    @DisplayName("Entwürfe: nur Autor überschreibt/löscht, Partei sieht mit, Eingaben und Anzahl begrenzt")
    void drafts() {
        town();
        var books = server.lawBooks();
        var draft = books.saveDraft(caller(alice), Optional.empty(), Optional.empty(), "  Entwurf ", " Text ");
        assertEquals("Entwurf", draft.title());
        assertEquals(1, books.draftsVisibleTo(caller(alice)).size());
        assertTrue(books.draftsVisibleTo(caller(bob)).isEmpty(), "ohne gemeinsame Partei unsichtbar");
        assertThrows(IllegalArgumentException.class, () -> books.saveDraft(caller(bob), Optional.of(draft.id()),
                Optional.empty(), "Bobs", "x"));
        assertThrows(IllegalArgumentException.class, () -> books.saveDraft(caller(alice), Optional.empty(),
                Optional.empty(), " ", "x"));
        assertThrows(IllegalArgumentException.class, () -> books.saveDraft(caller(alice), Optional.empty(),
                Optional.empty(), "t", "x".repeat(1001)));
        var updated = books.saveDraft(caller(alice), Optional.of(draft.id()), Optional.empty(), "Neu", "");
        assertEquals(draft.id(), updated.id());
        assertEquals(1, books.draftsVisibleTo(caller(alice)).size());
        assertFalse(books.deleteDraft(caller(bob), draft.id()));
        assertTrue(books.deleteDraft(caller(alice), draft.id()));
        assertFalse(books.deleteDraft(caller(alice), draft.id()));
        for (int i = 0; i < LawBookService.MAX_DRAFTS_PER_AUTHOR; i++) {
            books.saveDraft(caller(alice), Optional.empty(), Optional.empty(), "t" + i, "");
        }
        assertThrows(IllegalArgumentException.class, () -> books.saveDraft(caller(alice), Optional.empty(),
                Optional.empty(), "zu viel", ""));
    }

    @Test
    @DisplayName("Optional: Entwürfe überstehen einen Neustart, auch mit Tab, Zeilenumbruch und Backslash")
    void draftsPersistWhenEnabled(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        town();
        java.nio.file.Path file = dir.resolve("law-drafts.tsv");
        var first = new LawBookService(server.app(), server.calendar(), server.texts(), new de.etiennebader.civ.server.Announcements(),
                server.federations(), Optional.of(file));
        var saved = first.saveDraft(caller(alice), Optional.empty(), Optional.of(named("Nordhain").id()), "A\tB",
                "Zeile 1\nZeile 2 \\ Ende");
        var second = new LawBookService(server.app(), server.calendar(), server.texts(), new de.etiennebader.civ.server.Announcements(),
                server.federations(), Optional.of(file));
        assertEquals(List.of(saved), second.draftsVisibleTo(caller(alice)));
        assertTrue(second.deleteDraft(caller(alice), saved.id()));
        assertTrue(new LawBookService(server.app(), server.calendar(), server.texts(), new de.etiennebader.civ.server.Announcements(),
                server.federations(), Optional.of(file)).draftsVisibleTo(caller(alice)).isEmpty());
        assertTrue(new LawBookService(server.app(), server.calendar(), server.texts(), new de.etiennebader.civ.server.Announcements(),
                server.federations()).draftsVisibleTo(caller(alice)).isEmpty(), "Standard: flüchtig");
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
