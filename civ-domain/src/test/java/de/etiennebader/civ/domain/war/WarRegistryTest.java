package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt Kriegserklärung, Garnisonen, Gefechtsauflösung, Frieden und Neutralität ab (#27, erste Ausbaustufe). */
class WarRegistryTest {

    private final IdGenerator ids = new SeededIdGenerator(11);
    private final PolityId nation = new PolityId(UUID.randomUUID());
    private final PolityId land = new PolityId(UUID.randomUUID());
    private final ActorRef soldier = person();
    private final ActorRef officer = person();

    private static ActorRef person() {
        return new PersonRef(new PersonId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Kriegserklärung legt einen laufenden Krieg an; ein zweiter zwischen denselben Polities wird abgelehnt")
    void declareWarRejectsDuplicate() {
        WarRegistry registry = WarRegistry.empty();
        War war = registry.declareWar(ids, nation, land, Day.ZERO);
        assertTrue(war.ongoing());
        assertEquals(Optional.of(war), registry.ongoingWarBetween(nation, land));
        assertEquals(Optional.of(war), registry.ongoingWarBetween(land, nation), "Reihenfolge ist egal");
        assertThrows(WarAlreadyOngoingException.class, () -> registry.declareWar(ids, land, nation, Day.ZERO));
    }

    @Test
    @DisplayName("Ein neutraler Verteidiger kann nicht angegriffen werden")
    void declareWarRejectsNeutralDefender() {
        WarRegistry registry = WarRegistry.empty();
        registry.purchaseNeutrality(land, new Day(10), Day.ZERO);
        assertThrows(DefenderIsNeutralException.class, () -> registry.declareWar(ids, nation, land, new Day(5)));
        // nach Ablauf der Neutralität geht es wieder
        War war = registry.declareWar(ids, nation, land, new Day(11));
        assertTrue(war.ongoing());
    }

    @Test
    @DisplayName("Neutralität lässt sich nicht während eines laufenden Kriegs erkaufen")
    void cannotPurchaseNeutralityAtWar() {
        WarRegistry registry = WarRegistry.empty();
        registry.declareWar(ids, nation, land, Day.ZERO);
        assertThrows(AtWarException.class, () -> registry.purchaseNeutrality(land, new Day(5), Day.ZERO));
        assertThrows(AtWarException.class, () -> registry.purchaseNeutrality(nation, new Day(5), Day.ZERO));
    }

    @Test
    @DisplayName("Garnison: Aufnahme, Doppel-Aufnahme abgelehnt, Entlassung, Entlassung ohne Dienst abgelehnt")
    void enlistAndDischarge() {
        WarRegistry registry = WarRegistry.empty();
        registry.enlist(nation, soldier, MilitaryRank.SOLDIER, Day.ZERO);
        registry.enlist(nation, officer, MilitaryRank.OFFICER, Day.ZERO);
        assertEquals(1, registry.soldiersOf(nation));
        assertEquals(1, registry.officersOf(nation));
        assertThrows(AlreadyEnlistedException.class, () -> registry.enlist(nation, soldier, MilitaryRank.OFFICER, Day.ZERO));

        registry.discharge(nation, soldier, new Day(1));
        assertEquals(0, registry.soldiersOf(nation));
        assertThrows(NotEnlistedException.class, () -> registry.discharge(nation, soldier, new Day(1)));
    }

    @Test
    @DisplayName("Gefecht: die stärkere Garnison gewinnt, der Krieg endet mit Reparationen zu ihren Gunsten")
    void resolveBattleEndsWarWithReparationsForWinner() {
        WarRegistry registry = WarRegistry.empty();
        War war = registry.declareWar(ids, nation, land, Day.ZERO);
        // Angreifer: 10 Soldaten, Verteidiger (mit Heimvorteil) nur 1 Soldat -> Angreifer gewinnt klar
        for (int i = 0; i < 10; i++) {
            registry.enlist(nation, person(), MilitaryRank.SOLDIER, Day.ZERO);
        }
        registry.enlist(land, person(), MilitaryRank.SOLDIER, Day.ZERO);

        BattleOutcome outcome = registry.resolveBattle(war.id(), new Day(1));

        assertEquals(BattleOutcome.AGGRESSOR_WINS, outcome);
        War ended = registry.war(war.id()).orElseThrow();
        assertFalse(ended.ongoing());
        assertEquals(Optional.of(new Day(1)), ended.endedOn());
        assertTrue(ended.outcome().isPresent());
        assertEquals(nation, ended.outcome().get().winner());
        assertEquals(land, ended.outcome().get().loser());
        assertTrue(ended.outcome().get().points() > 0, "klarer Sieg soll Siegpunkte > 0 ergeben");
        assertTrue(registry.ongoingWarsOf(nation).isEmpty(), "beendeter Krieg zählt nicht mehr als laufend");
    }

    @Test
    @DisplayName("Ein Unentschieden beendet den Krieg nicht")
    void drawKeepsWarOngoing() {
        WarRegistry registry = WarRegistry.empty();
        War war = registry.declareWar(ids, nation, land, Day.ZERO);
        // Angreifer 1 Offizier + 8 Soldaten = 11, Verteidiger 10 Soldaten * 1,1 = 11 -> exakter Gleichstand
        registry.enlist(nation, person(), MilitaryRank.OFFICER, Day.ZERO);
        for (int i = 0; i < 8; i++) {
            registry.enlist(nation, person(), MilitaryRank.SOLDIER, Day.ZERO);
        }
        for (int i = 0; i < 10; i++) {
            registry.enlist(land, person(), MilitaryRank.SOLDIER, Day.ZERO);
        }

        BattleOutcome outcome = registry.resolveBattle(war.id(), new Day(1));

        assertEquals(BattleOutcome.DRAW, outcome);
        assertTrue(registry.war(war.id()).orElseThrow().ongoing(), "ohne Sieger geht der Krieg weiter");
        // ein weiteres Gefecht ist möglich, solange der Krieg läuft
        registry.enlist(nation, person(), MilitaryRank.OFFICER, Day.ZERO);
        BattleOutcome second = registry.resolveBattle(war.id(), new Day(2));
        assertEquals(BattleOutcome.AGGRESSOR_WINS, second);
    }

    @Test
    @DisplayName("Ein ausgehandelter Frieden beendet den Krieg ohne Sieger und ohne Reparationen")
    void negotiatedPeaceEndsWarWithoutWinner() {
        WarRegistry registry = WarRegistry.empty();
        War war = registry.declareWar(ids, nation, land, Day.ZERO);
        registry.negotiatePeace(war.id(), new Day(3));
        War ended = registry.war(war.id()).orElseThrow();
        assertFalse(ended.ongoing());
        assertTrue(ended.outcome().isEmpty());
    }

    @Test
    @DisplayName("Unbekannter bzw. bereits beendeter Krieg lässt sich nicht erneut auflösen")
    void unknownOrEndedWarRejected() {
        WarRegistry registry = WarRegistry.empty();
        assertThrows(UnknownWarException.class, () -> registry.resolveBattle(WarId.generate(ids), Day.ZERO));

        War war = registry.declareWar(ids, nation, land, Day.ZERO);
        registry.negotiatePeace(war.id(), Day.ZERO);
        assertThrows(WarAlreadyEndedException.class, () -> registry.resolveBattle(war.id(), new Day(1)));
        assertThrows(WarAlreadyEndedException.class, () -> registry.negotiatePeace(war.id(), new Day(1)));
    }

    @Test
    @DisplayName("Jede Zustandsänderung meldet genau das erwartete Ereignis über pullEvents()")
    void raisesExpectedEvents() {
        WarRegistry registry = WarRegistry.empty();
        War war = registry.declareWar(ids, nation, land, Day.ZERO);
        registry.enlist(nation, soldier, MilitaryRank.SOLDIER, Day.ZERO);
        registry.discharge(nation, soldier, Day.ZERO);
        registry.purchaseNeutrality(new PolityId(UUID.randomUUID()), new Day(5), Day.ZERO);
        registry.negotiatePeace(war.id(), new Day(1));

        var events = registry.pullEvents();
        assertEquals(5, events.size());
        assertTrue(events.get(0) instanceof WarEvent.WarDeclared);
        assertTrue(events.get(1) instanceof WarEvent.Enlisted);
        assertTrue(events.get(2) instanceof WarEvent.Discharged);
        assertTrue(events.get(3) instanceof WarEvent.NeutralityPurchased);
        assertTrue(events.get(4) instanceof WarEvent.WarEnded);
        assertTrue(registry.pullEvents().isEmpty(), "pullEvents leert die Liste");
    }

    @Test
    @DisplayName("Befehlskette: höhere Ränge befehligen, nur ein General, Beförderung behält Ausbildung")
    void chainOfCommand() {
        WarRegistry registry = WarRegistry.empty();
        ActorRef general = person();
        ActorRef rival = person();
        registry.enlist(nation, soldier, MilitaryRank.SOLDIER, Day.ZERO);
        registry.enlist(nation, general, MilitaryRank.SOLDIER, Day.ZERO);
        registry.enlist(nation, rival, MilitaryRank.SOLDIER, Day.ZERO);
        registry.promote(nation, general, MilitaryRank.GENERAL);
        assertTrue(registry.commands(nation, general, soldier));
        assertFalse(registry.commands(nation, soldier, general));
        assertFalse(registry.commands(nation, soldier, rival), "Gleiche befehligen sich nicht");
        assertFalse(registry.commands(nation, general, person()), "Zivilisten stehen außerhalb der Kette");
        registry.promote(nation, rival, MilitaryRank.GENERAL);
        assertEquals(MilitaryRank.GENERAL, registry.garrisonOf(nation).get(rival));
        assertEquals(MilitaryRank.CAPTAIN, registry.garrisonOf(nation).get(general), "es gibt nur einen General");
        assertTrue(MilitaryRank.CAPTAIN.commandsCampaigns());
        assertFalse(MilitaryRank.OFFICER.commandsCampaigns());
        assertThrows(NotEnlistedException.class, () -> registry.promote(nation, person(), MilitaryRank.OFFICER));
        assertTrue(MilitaryRank.GENERAL.power() > MilitaryRank.OFFICER.power());
    }
}
