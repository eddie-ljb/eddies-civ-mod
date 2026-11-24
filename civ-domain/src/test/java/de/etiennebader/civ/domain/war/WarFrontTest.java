package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Front, Feldzüge, Verwundete/Lazarett, Ausspäher, Training, Bündnisse und Ansprüche (#27). */
class WarFrontTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(5);
    private final PolityId north = new PolityId(UUID.randomUUID());
    private final PolityId south = new PolityId(UUID.randomUUID());
    private final PolityId east = new PolityId(UUID.randomUUID());
    private final WarRegistry registry = WarRegistry.empty();

    private static ActorRef person() {
        return new PersonRef(new PersonId(UUID.randomUUID()));
    }

    private void raise(PolityId polity, int soldiers) {
        for (int i = 0; i < soldiers; i++) {
            registry.enlist(polity, person(), MilitaryRank.SOLDIER, Day.ZERO);
        }
    }

    @Test
    @DisplayName("Ein Gefecht verschiebt die Front und verwundet; Gleichstand verschiebt nichts")
    void fightShiftsFront() {
        War war = registry.declareWar(ids, north, south, Day.ZERO);
        raise(north, 20);
        raise(south, 10);
        BattleReport report = registry.fight(war.id(), north, BattleKind.ASSAULT, new Day(1));
        assertTrue(report.frontShift() > 0);
        assertEquals(report.front(), registry.frontOf(war.id()));
        assertEquals(2, report.woundedAggressor());
        assertEquals(3, report.woundedDefender());
        assertEquals(0, registry.power(north, new Day(1)) - registry.power(north, new Day(1)));
        assertTrue(registry.power(north, new Day(1)) < 2000, "Verwundete fehlen in der Kampfkraft");
    }

    @Test
    @DisplayName("Mehrere Gefechte entscheiden den Krieg mit Reparationen und Gebietsforderung")
    void frontDecidesWar() {
        War war = registry.declareWar(ids, north, south, Day.ZERO);
        raise(north, 40);
        raise(south, 5);
        BattleReport last = null;
        for (int day = 1; day <= 5 && registry.war(war.id()).orElseThrow().ongoing(); day++) {
            last = registry.fight(war.id(), north, BattleKind.ASSAULT, new Day(day));
        }
        assertFalse(registry.war(war.id()).orElseThrow().ongoing());
        PeaceTerms terms = last.decided().orElseThrow();
        assertEquals(north, terms.winner());
        assertTrue(terms.points() >= 1, "ein Sieg bringt mindestens einen Siegpunkt");
        assertFalse(terms.settled(), "die Punkte sind noch nicht verteilt");
        assertEquals(0, terms.conquest());
        assertEquals(0, terms.reparations());
        assertEquals(1, registry.pendingSpoils().size());
        PeaceTerms settled = registry.settleSpoils(war.id(), 30, 1, new Day(6));
        assertTrue(settled.settled());
        assertEquals(1, settled.conquest());
        assertEquals(30, settled.reparations());
        assertTrue(registry.pendingSpoils().isEmpty());
        assertThrows(WarRuleException.class, () -> registry.settleSpoils(war.id(), 0, 0, new Day(6)));
    }

    @Test
    @DisplayName("Lazarett verkürzt die Ausfallzeit, Genesung stellt die Kampfkraft wieder her")
    void hospitalShortensRecovery() {
        War war = registry.declareWar(ids, north, south, Day.ZERO);
        raise(north, 10);
        raise(south, 10);
        registry.buildHospitalBeds(north, 5);
        registry.fight(war.id(), north, BattleKind.ASSAULT, new Day(1));
        long hurt = registry.wounded().get(north).size();
        assertTrue(hurt > 0);
        assertTrue(registry.wounded().get(north).values().stream().allMatch(day -> day.equals(new Day(1).plus(WarRegistry.HOSPITAL_RECOVERY_DAYS))));
        assertTrue(registry.wounded().get(south).values().stream().allMatch(day -> day.equals(new Day(1).plus(WarRegistry.FIELD_RECOVERY_DAYS))));
        registry.heal(new Day(3));
        assertTrue(registry.wounded().get(north).isEmpty());
        assertFalse(registry.wounded().get(south).isEmpty());
    }

    @Test
    @DisplayName("Hinterhalt braucht Ausspäher, Verteidigung nur der Verteidiger; Feldzüge laufen am Plantag")
    void campaigns() {
        War war = registry.declareWar(ids, north, south, Day.ZERO);
        raise(north, 5);
        raise(south, 5);
        assertThrows(WarRuleException.class, () -> registry.planCampaign(war.id(), north, BattleKind.AMBUSH, new Day(2), Day.ZERO));
        assertThrows(WarRuleException.class, () -> registry.planCampaign(war.id(), north, BattleKind.DEFENSIVE, new Day(2), Day.ZERO));
        registry.enlist(north, person(), MilitaryRank.SCOUT, Day.ZERO);
        registry.planCampaign(war.id(), north, BattleKind.AMBUSH, new Day(2), Day.ZERO);
        registry.planCampaign(war.id(), south, BattleKind.DEFENSIVE, new Day(2), Day.ZERO);
        assertTrue(registry.runDueCampaigns(new Day(1)).isEmpty());
        assertEquals(2, registry.runDueCampaigns(new Day(2)).size());
        assertTrue(registry.campaigns().isEmpty());
    }

    @Test
    @DisplayName("Ausspäher liefern genaue Aufklärung, sonst nur die grobe Größe")
    void intel() {
        raise(south, 4);
        registry.enlist(north, person(), MilitaryRank.SCOUT, Day.ZERO);
        Intel exact = registry.intel(north, south, Day.ZERO);
        assertTrue(exact.exact());
        assertEquals(400, exact.power());
        Intel blind = registry.intel(south, north, Day.ZERO);
        assertFalse(blind.exact());
        assertEquals(0, blind.power());
        assertEquals(1, blind.members());
    }

    @Test
    @DisplayName("Ausbildung und Ausrüstung erhöhen die Kampfkraft bis zum Maximum")
    void trainingAndEquipment() {
        raise(north, 2);
        assertEquals(200, registry.power(north, Day.ZERO));
        assertEquals(2, registry.train(north, Day.ZERO));
        assertEquals(2, registry.equip(north, Day.ZERO));
        assertEquals(240, registry.power(north, Day.ZERO));
        for (int i = 0; i < WarRegistry.MAX_TRAINING - 1; i++) {
            registry.train(north, Day.ZERO);
        }
        assertEquals(0, registry.trainable(north));
        assertThrows(WarRuleException.class, () -> registry.train(north, Day.ZERO));
    }

    @Test
    @DisplayName("Bündnis braucht Antrag und Annahme; Verbündete stützen sich im Krieg und können sich nicht angreifen")
    void alliances() {
        assertFalse(registry.proposeAlliance(south, east, Day.ZERO));
        assertTrue(registry.alliesOf(south).isEmpty());
        assertTrue(registry.acceptAlliance(east, south, Day.ZERO));
        assertTrue(registry.alliesOf(east).contains(south));
        assertThrows(WarRuleException.class, () -> registry.declareWar(ids, east, south, Day.ZERO));

        raise(east, 10);
        War war = registry.declareWar(ids, north, south, Day.ZERO);
        raise(south, 2);
        assertEquals(200 + 500, registry.sidePower(war, south, Day.ZERO), "Verbündeter zählt zur Hälfte");
        registry.breakAlliance(south, east, Day.ZERO);
        assertEquals(200, registry.sidePower(war, south, Day.ZERO));
    }

    @Test
    @DisplayName("Ansprüche: nicht doppelt und nicht gegen sich selbst")
    void claims() {
        registry.lodgeClaim(north, south, Day.ZERO);
        assertTrue(registry.hasClaim(north, south));
        assertEquals(1, registry.claimsAgainst(south));
        assertThrows(WarRuleException.class, () -> registry.lodgeClaim(north, south, Day.ZERO));
        assertThrows(WarRuleException.class, () -> registry.lodgeClaim(north, north, Day.ZERO));
    }

    @Test
    @DisplayName("Snapshot stellt den ganzen Zustand wieder her")
    void snapshotRoundTrip() {
        War war = registry.declareWar(ids, north, south, Day.ZERO);
        raise(north, 4);
        raise(south, 4);
        registry.train(north, Day.ZERO);
        registry.fight(war.id(), north, BattleKind.ASSAULT, new Day(1));
        registry.lodgeClaim(north, south, Day.ZERO);
        WarRegistry copy = WarRegistry.restore(registry.snapshot());
        assertEquals(registry.snapshot(), copy.snapshot());
    }
}
