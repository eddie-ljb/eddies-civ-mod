package de.etiennebader.civ.server.justice;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.justice.Stage;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JusticeServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(17);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carla = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    @Test
    @DisplayName("Strafprozess: Klage, Eröffnung, Beweise, Aussage, Plädoyer, Urteil, Rechtskraft")
    void criminalTrial() {
        setUp();
        JusticeService justice = server.justice();
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertFalse(justice.file(caller(bob), town, "irgendwas", "Carla | x").success());
        assertFalse(justice.file(caller(bob), town, "straf", "Carla ohne Trennstrich").success());
        Reply filed = justice.file(caller(bob), town, "straf", "Carla | Diebstahl von Weizen");
        assertTrue(filed.success(), filed.toString());
        CourtCase found = server.queries().justice().cases().getLast();
        int number = found.number();
        assertFalse(justice.open(caller(bob), number).success(), "nur Richter");
        assertTrue(justice.open(caller(alice), number).success());
        assertTrue(justice.evidence(caller(bob), number, "buch", "Tagebuch", "Carla nahm den Weizen.").success());
        Reply statement = justice.testify(caller(carla), number, "Ich war es nicht, ich war zuhause.");
        assertTrue(statement.success(), statement.toString());
        assertTrue(justice.show(caller(carla), number).success());
        assertTrue(justice.advance(caller(alice), number).success());
        assertEquals(Stage.PLEADINGS, justice.courtCase(number).orElseThrow().stage());
        assertTrue(justice.plead(caller(bob), number, "Die Beweise sind eindeutig, schuldig!").success());
        assertFalse(justice.decide(caller(alice), number, "vielleicht").success());
        Reply verdict = justice.decide(caller(alice), number, "schuldig 5 | Beweise eindeutig");
        assertTrue(verdict.success(), verdict.toString());
        assertEquals(MessageKey.CASE_VERDICT.key(), verdict.lines().getFirst().key());
        assertEquals(500L, justice.courtCase(number).orElseThrow().verdict().orElseThrow().fine());
        assertFalse(justice.appeal(caller(bob), number).success(), "nur die unterlegene Seite");
        for (int day = 0; day < 6 && justice.courtCase(number).orElseThrow().stage() != Stage.FINAL; day++) {
            server.endDayNow();
        }
        assertEquals(Stage.FINAL, justice.courtCase(number).orElseThrow().stage());
        assertTrue(justice.mine(caller(carla)).success());
        assertTrue(justice.court(caller(alice), town).success());
        assertEquals(1, justice.casesOf(carla).size());
        assertTrue(justice.caseView(number, Optional.of(carla)).orElseThrow().myRole().isPresent());
    }

    @Test
    @DisplayName("Zivilklage ohne Zutun: das Gericht verhandelt nach Fristen und entscheidet nach Aktenlage")
    void civilCaseRunsByItself() {
        setUp();
        JusticeService justice = server.justice();
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertFalse(justice.file(caller(bob), town, "zivil", "Carla | ohne Betrag").success());
        assertTrue(justice.file(caller(bob), town, "zivil", "Carla 3 | Unbezahlte Lieferung").success());
        int number = server.queries().justice().cases().getLast().number();
        for (int day = 0; day < 15 && justice.courtCase(number).orElseThrow().stage() != Stage.FINAL; day++) {
            server.endDayNow();
        }
        CourtCase done = justice.courtCase(number).orElseThrow();
        assertEquals(Stage.FINAL, done.stage());
        assertTrue(done.verdict().isPresent());
    }

    @Test
    @DisplayName("Personal: nur die Spitze ernennt Richter")
    void appointJudge() {
        setUp();
        JusticeService justice = server.justice();
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertFalse(justice.appoint(caller(bob), town, de.etiennebader.civ.domain.justice.Role.JUDGE, "Carla",
                Optional.empty(), true).success());
        assertTrue(justice.appoint(caller(alice), town, de.etiennebader.civ.domain.justice.Role.JUDGE, "Carla",
                Optional.empty(), true).success());
        assertEquals(1, server.queries().justice().staffOf(town.id()).judges().size());
    }

    private void setUp() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(carla, "Carla");
        assertTrue(server.commands().found(caller(alice), "Nordhain").success());
        assertTrue(server.commands().join(caller(bob), "Nordhain").success());
        assertTrue(server.commands().join(caller(carla), "Nordhain").success());
        assertTrue(server.bank().deposit(caller(bob), 20).success());
        assertTrue(server.bank().deposit(caller(carla), 20).success());
    }

    private static Caller caller(PlayerRef player) {
        return new Caller(player, "?", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }
}
