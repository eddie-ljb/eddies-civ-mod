package de.etiennebader.civ.server.police;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.police.CrimeKind;
import de.etiennebader.civ.domain.police.PoliceRank;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliceServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(84);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef carol = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef dave = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private PolityId town;

    private Caller caller(PlayerRef player, String name) {
        return new Caller(player, name, new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }

    @BeforeEach
    void setUp() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        server.playerJoined(carol, "Carol");
        server.playerJoined(dave, "Dave");
        assertTrue(server.commands().found(caller(alice, "Alice"), "Nordhain").success());
        for (String name : new String[]{"Bob", "Carol"}) {
            PlayerRef p = name.equals("Bob") ? bob : carol;
            assertTrue(server.commands().join(caller(p, name), "Nordhain").success());
        }
        town = server.queries().polityNamed("Nordhain").orElseThrow().id();
    }

    @Test
    @DisplayName("Dienst: Bürgermeister wird Kommissar, Auswärtige kommen nicht hinein, Kadetten dürfen nicht festnehmen")
    void serviceAndPowers() {
        assertFalse(server.police().join(caller(dave, "Dave")).success(), "keine Gemeinde");
        assertTrue(server.police().join(caller(alice, "Alice")).success());
        assertEquals(PoliceRank.COMMISSIONER, server.police().registry().rankOf(town, alice).orElseThrow());
        assertFalse(server.police().join(caller(alice, "Alice")).success(), "doppelt");
        assertTrue(server.police().join(caller(bob, "Bob")).success());
        assertEquals(PoliceRank.CADET, server.police().registry().rankOf(town, bob).orElseThrow());
        assertFalse(server.police().arrest(caller(bob, "Bob"), "Carol").success(), "Kadett darf nicht festnehmen");
    }

    @Test
    @DisplayName("Befehlskette: Kadett befördert niemanden, Kommissar nicht über sich hinaus, Bürgermeister alles")
    void appointRules() {
        server.police().join(caller(alice, "Alice"));
        server.police().join(caller(bob, "Bob"));
        assertFalse(server.police().appoint(caller(bob, "Bob"), "Carol", PoliceRank.CADET).success());
        assertFalse(server.police().appoint(caller(alice, "Alice"), "Dave", PoliceRank.CADET).success(), "kein Mitglied");
        assertTrue(server.police().appoint(caller(alice, "Alice"), "Bob", PoliceRank.SERGEANT).success());
        assertTrue(server.police().appoint(caller(bob, "Bob"), "Carol", PoliceRank.OFFICER).success());
        assertFalse(server.police().appoint(caller(bob, "Bob"), "Carol", PoliceRank.SERGEANT).success(),
                "nicht auf den eigenen Rang");
        assertFalse(server.police().dismiss(caller(carol, "Carol"), "Bob").success(), "Untergebene entlassen keinen Chef");
        assertTrue(server.police().dismiss(caller(bob, "Bob"), "Carol").success());
    }

    @Test
    @DisplayName("Festnahme: braucht Anzeige, Zelle und Rang; Vorgesetzte und Bürgermeister sind tabu")
    void arrest() {
        server.police().join(caller(alice, "Alice"));
        server.police().appoint(caller(alice, "Alice"), "Bob", PoliceRank.OFFICER);
        assertFalse(server.police().arrest(caller(bob, "Bob"), "Carol").success(), "ohne Anzeige");
        assertFalse(server.police().arrest(caller(bob, "Bob"), "Alice").success(), "Bürgermeister/Vorgesetzte");
        assertFalse(server.police().arrest(caller(bob, "Bob"), "Bob").success());
        assertTrue(server.police().report(caller(alice, "Alice"), "Carol", CrimeKind.THEFT).success());
        assertFalse(server.police().report(caller(alice, "Alice"), "Alice", CrimeKind.THEFT).success());

        var noCell = server.police().arrest(caller(bob, "Bob"), "Carol");
        assertTrue(noCell.success());
        assertTrue(server.police().restrained(carol), "gefesselt trotz fehlender Zelle");
        assertEquals(0, server.police().registry().detainees(town).size());

        assertFalse(server.police().cells(caller(bob, "Bob"), 2).success(), "nur der Bürgermeister baut Zellen");
        assertFalse(server.police().cells(caller(alice, "Alice"), 99).success());
        assertTrue(server.police().cells(caller(alice, "Alice"), 2).success());
        assertTrue(server.police().report(caller(alice, "Alice"), "Carol", CrimeKind.ASSAULT).success());
        assertTrue(server.police().arrest(caller(bob, "Bob"), "Carol").success());
        assertEquals(1, server.police().registry().detainees(town).size());
        assertTrue(server.police().restrained(carol));
        assertFalse(server.police().release(caller(bob, "Bob"), "Carol").success(), "Polizist darf nicht freilassen");
        assertTrue(server.police().release(caller(alice, "Alice"), "Carol").success());
        assertFalse(server.police().restrained(carol));
    }

    @Test
    @DisplayName("Handschellen lassen sich nur von Befugten anlegen/lösen und enden nach einem Tag")
    void cuffs() {
        server.police().join(caller(alice, "Alice"));
        server.police().appoint(caller(alice, "Alice"), "Bob", PoliceRank.OFFICER);
        assertFalse(server.police().cuff(caller(carol, "Carol"), "Dave").success());
        assertTrue(server.police().cuff(caller(bob, "Bob"), "Carol").success());
        assertTrue(server.police().restrained(carol));
        assertFalse(server.police().uncuff(caller(carol, "Carol"), "Carol").success());
        server.police().endOfDay(Day.of(1_000));
        assertFalse(server.police().restrained(carol), "Handschellen verfallen");
        assertFalse(server.police().uncuff(caller(bob, "Bob"), "Carol").success(), "nichts zu lösen");
    }

    @Test
    @DisplayName("Ohne Spieler stellt die Gemeinde Dorfbewohner ein, befördert sie und hält die Zellen-Obergrenze")
    void villagersStaffThemselves() {
        BlockPosition here = new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8);
        for (int i = 0; i < 16; i++) {
            server.villagers().seen(new de.etiennebader.civ.domain.actor.PersonRef(
                    de.etiennebader.civ.domain.shared.PersonId.generate(ids)), here, Day.of(1));
        }
        assertTrue(server.police().cells(caller(alice, "Alice"), 2).success());
        for (long day = 2; day < 80; day++) {
            server.police().endOfDay(Day.of(day));
            assertTrue(server.police().registry().detainees(town).size() <= 2, "nie mehr Insassen als Zellen");
        }
        var registry = server.police().registry();
        assertTrue(registry.strength(town) >= 2, "Dorfbewohner treten in die Polizei ein");
        assertTrue(registry.strength(town) <= PoliceService.MAX_FORCE);
        assertEquals(1, registry.count(town, PoliceRank.COMMISSIONER), "genau ein Kommissar");
        assertTrue(registry.forceOf(town).keySet().stream()
                .allMatch(m -> m instanceof de.etiennebader.civ.domain.actor.PersonRef));
    }
}
