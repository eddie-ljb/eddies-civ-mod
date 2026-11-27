package de.etiennebader.civ.domain.police;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliceRegistryTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(84);
    private final PolityId town = PolityId.generate(ids);
    private final ActorRef chief = new PlayerRef(PlayerId.generate(ids));
    private final ActorRef cadet = new PlayerRef(PlayerId.generate(ids));
    private final ActorRef thief = new PlayerRef(PlayerId.generate(ids));
    private final PoliceRegistry police = PoliceRegistry.empty();

    @Test
    @DisplayName("Befehlskette: nur höherer Rang befehligt, Gleiche und Zivilisten nicht")
    void chainOfCommand() {
        police.appoint(town, chief, PoliceRank.COMMISSIONER);
        police.appoint(town, cadet, PoliceRank.CADET);
        assertTrue(police.commands(town, chief, cadet));
        assertFalse(police.commands(town, cadet, chief));
        assertFalse(police.commands(town, chief, chief));
        assertFalse(police.commands(town, chief, thief));
        assertTrue(PoliceRank.OFFICER.mayArrest());
        assertFalse(PoliceRank.CADET.mayArrest());
    }

    @Test
    @DisplayName("Zellen: ohne Zelle keine Haft, volle Zellen, doppelte Haft und Ablauf")
    void cells() {
        assertFalse(police.detain(town, thief, Day.of(1), 2, true), "keine Zelle gebaut");
        police.setCells(town, 1);
        police.cuff(thief, Day.of(1));
        assertTrue(police.detain(town, thief, Day.of(1), 2, true));
        assertFalse(police.restrained(cadet, Day.of(1)));
        assertTrue(police.restrained(thief, Day.of(2)), "Inhaftierte sind handlungsunfähig");
        assertEquals(0, police.freeCells(town));
        assertFalse(police.detain(town, cadet, Day.of(1), 1, true), "Zelle voll");
        assertFalse(police.detain(town, thief, Day.of(1), 1, true), "bereits inhaftiert");
        assertTrue(police.releaseDue(Day.of(2)).isEmpty());
        assertEquals(1, police.releaseDue(Day.of(3)).size());
        assertEquals(1, police.freeCells(town));
        assertFalse(police.restrained(thief, Day.of(3)));
    }

    @Test
    @DisplayName("Handschellen halten einen Tag und lassen sich lösen")
    void cuffs() {
        police.cuff(thief, Day.of(5));
        assertTrue(police.restrained(thief, Day.of(5)));
        assertFalse(police.restrained(thief, Day.of(6)));
        police.expireCuffs(Day.of(6));
        police.cuff(thief, Day.of(7));
        assertTrue(police.uncuff(thief));
        assertFalse(police.uncuff(thief));
        assertFalse(police.restrained(thief, Day.of(7)));
    }

    @Test
    @DisplayName("Anzeigen werden nummeriert und bearbeitet; Zellenzahl ist begrenzt; Zustand überlebt Wiederherstellung")
    void reportsAndSnapshot() {
        Offense first = police.report(town, thief, CrimeKind.THEFT, Day.of(1));
        Offense second = police.report(town, cadet, CrimeKind.ASSAULT, Day.of(1));
        assertEquals(1, first.number());
        assertEquals(2, second.number());
        police.handle(town, 1);
        assertEquals(1, police.openOffenses(town).size());
        police.setCells(town, 2);
        police.appoint(town, chief, PoliceRank.SERGEANT);
        PoliceRegistry restored = PoliceRegistry.restore(police.snapshot());
        assertEquals(2, restored.offenses(town).size());
        assertEquals(1, restored.openOffenses(town).size());
        assertEquals(2, restored.cells(town));
        assertEquals(PoliceRank.SERGEANT, restored.rankOf(town, chief).orElseThrow());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> police.setCells(town, PoliceRegistry.MAX_CELLS + 1));
    }

    @Test
    @DisplayName("Kriminalität ist begrenzt und sinkt mit Bildung, Ordnung, Friedfertigkeit und Verbundenheit")
    void criminality() {
        int high = Criminality.of(0, -100, -100, -100);
        int low = Criminality.of(100, 100, 100, 100);
        assertTrue(high > 20 && high <= Criminality.MAX);
        assertEquals(0, low);
        assertTrue(Criminality.of(50, 0, 0, 0) < 10, "im Durchschnitt gering");
        assertEquals(CrimeKind.MURDER, Criminality.kindFor(high, 99));
        assertEquals(CrimeKind.THEFT, Criminality.kindFor(5, 99), "niedrige Kriminalität mordet nicht");
    }

    @Test
    @DisplayName("Berufung: Verknüpfung wandert aufs neue Verfahren, Zeugen werden neu geladen; Gefesselte werden aufgelistet")
    void appealMovesLink() {
        police.link(new CaseLink(5, town, thief, CrimeKind.ASSAULT, 4, java.util.List.of(chief), true));
        assertTrue(police.moveLink(5, 9));
        assertTrue(police.linkOf(5).isEmpty(), "die Vorinstanz vollstreckt nichts mehr");
        CaseLink moved = police.linkOf(9).orElseThrow();
        assertEquals(4, moved.sentenceDays());
        assertFalse(moved.prepared());
        assertFalse(police.moveLink(5, 10), "nur einmal");
        police.cuff(cadet, Day.of(1));
        police.setCells(town, 1);
        police.detain(town, thief, Day.of(1), 3, true);
        assertEquals(2, police.restrainedActors(Day.of(1)).size());
        assertEquals(1, police.restrainedActors(Day.of(2)).size(), "Handschellen verfallen, Haft bleibt");
    }
}
