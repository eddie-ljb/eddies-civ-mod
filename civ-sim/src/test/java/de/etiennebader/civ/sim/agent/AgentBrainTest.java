package de.etiennebader.civ.sim.agent;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBrainTest {

    private static final double LEAVE_THRESHOLD = 0.2;

    private final AgentBrain brain = new AgentBrain(0);
    private final SplittableRandom random = new SplittableRandom(1);

    @Test
    @DisplayName("Ohne Bedürfnisse ruht ein Akteur")
    void restsWithoutNeeds() {
        Agent agent = agent(0.5, 0.5, 0.5, 0.0);

        assertEquals(AgentAction.REST, brain.decide(agent, situation(false, false, false, 0, 0), random));
    }

    @Test
    @DisplayName("Ehrgeiziger Heimatloser mit Kapital gründet, statt einer schwachen Gemeinde beizutreten")
    void ambitiousAgentFounds() {
        Agent agent = agent(1.0, 0.1, 0.5, 0.0);
        growBelonging(agent);

        AgentAction action = brain.decide(agent, situation(false, false, true, 0.2, 0), random);

        assertEquals(AgentAction.FOUND_POLITY, action);
    }

    @Test
    @DisplayName("Geselliger Heimatloser tritt einer attraktiven Gemeinde bei")
    void sociableAgentJoins() {
        Agent agent = agent(0.0, 1.0, 0.5, 0.0);
        growBelonging(agent);

        assertEquals(AgentAction.JOIN_POLITY, brain.decide(agent, situation(false, false, true, 0.9, 0), random));
    }

    @Test
    @DisplayName("Unzufriedenes Mitglied tritt aus, das einzige Mitglied nie")
    void unhappyMemberLeaves() {
        Agent agent = member();
        agent.adjustSatisfaction(-1);

        assertEquals(AgentAction.LEAVE_POLITY, brain.decide(agent, memberSituation(false, false), random));
        assertFalse(brain.utilities(agent, memberSituation(true, false)).containsKey(AgentAction.LEAVE_POLITY));
    }

    @Test
    @DisplayName("Mitglied ohne Grundstück kauft, sobald das Wohnbedürfnis groß genug ist")
    void homelessMemberBuysPlot() {
        Agent agent = member();
        for (int day = 0; day < 10; day++) {
            agent.needs().update(true, true, 1_000_000, 10, 0, 0.1);
        }

        assertEquals(AgentAction.BUY_PLOT, brain.decide(agent, memberSituation(false, true), random));
    }

    @Test
    @DisplayName("Bargeld: abheben bei Lücke, einzahlen bei Überschuss")
    void managesCash() {
        Agent cashLover = agent(0.5, 0.5, 0.5, 1.0);
        assertEquals(AgentAction.WITHDRAW_CASH, brain.decide(cashLover, situation(false, false, false, 0, 1000), random));

        Agent hoarder = agent(0.5, 0.5, 0.5, 0.0);
        hoarder.receiveCash(1000);
        Map<AgentAction, Double> utilities = brain.utilities(hoarder, situation(false, false, false, 0, -1000));
        assertTrue(utilities.get(AgentAction.DEPOSIT_CASH) > utilities.get(AgentAction.REST));
    }

    private static Agent agent(double ambition, double sociability, double thrift, double cashAffinity) {
        return new Agent(PlayerRef.of(UUID.randomUUID()), AgentKind.PLAYER,
                new Personality(ambition, sociability, thrift, cashAffinity, 1.0));
    }

    private static Agent member() {
        Agent agent = agent(0.5, 0.5, 0.5, 0.0);
        agent.moveTo(PolityId.generate(new SeededIdGenerator(1)));
        return agent;
    }

    private static void growBelonging(Agent agent) {
        for (int day = 0; day < 30; day++) {
            agent.needs().update(false, false, 1_000_000, 10, 0.1, 0);
        }
    }

    private static AgentSituation situation(boolean member, boolean homeless, boolean canFound, double attraction,
                                            long cashGap) {
        return new AgentSituation(member, false, homeless, false, canFound, attraction,
                cashGap, Math.abs(cashGap), true, LEAVE_THRESHOLD);
    }

    private static AgentSituation memberSituation(boolean soleMember, boolean homelessWithOffer) {
        return new AgentSituation(true, soleMember, homelessWithOffer, homelessWithOffer, false, 0.5,
                0, 0, true, LEAVE_THRESHOLD);
    }
}
