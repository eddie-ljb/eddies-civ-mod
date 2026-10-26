package de.etiennebader.civ.sim.agent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Wählt die Handlung mit dem höchsten Nutzen. Jeder Nutzen liegt grob zwischen 0 und 1 und ergibt
 * sich aus Bedürfnissen, Persönlichkeit und Lage; ein kleines Rauschen verhindert, dass alle
 * Akteure im Gleichschritt handeln.
 */
public final class AgentBrain {

    private static final double REST_UTILITY = 0.15;

    private final double noise;

    public AgentBrain(double noise) {
        if (noise < 0) {
            throw new IllegalArgumentException("noise darf nicht negativ sein: " + noise);
        }
        this.noise = noise;
    }

    public AgentAction decide(Agent agent, AgentSituation situation, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        Map<AgentAction, Double> utilities = utilities(agent, situation);
        AgentAction best = AgentAction.REST;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<AgentAction, Double> entry : utilities.entrySet()) {
            double score = entry.getValue() + noise * random.nextDouble();
            if (entry.getValue() > 0 && score > bestScore) {
                best = entry.getKey();
                bestScore = score;
            }
        }
        return best;
    }

    /** Nutzen jeder Handlung; 0 heißt „kommt heute nicht in Frage“. In fester Enum-Reihenfolge. */
    public Map<AgentAction, Double> utilities(Agent agent, AgentSituation situation) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(situation, "situation");
        Personality personality = agent.personality();
        Needs needs = agent.needs();
        Map<AgentAction, Double> utilities = new EnumMap<>(AgentAction.class);

        utilities.put(AgentAction.REST, REST_UTILITY);
        if (!situation.member()) {
            if (situation.canAffordFounding()) {
                utilities.put(AgentAction.FOUND_POLITY,
                        needs.belonging() * personality.ambition() * (1 - 0.5 * situation.bestPolityAttraction()));
            }
            if (situation.bestPolityAttraction() > 0) {
                utilities.put(AgentAction.JOIN_POLITY,
                        needs.belonging() * (0.4 + 0.6 * personality.sociability()) * situation.bestPolityAttraction());
            }
        } else {
            if (!situation.soleMember() && agent.satisfaction() < situation.leaveThreshold()) {
                utilities.put(AgentAction.LEAVE_POLITY, 0.4 + 2 * (situation.leaveThreshold() - agent.satisfaction()));
            }
            if (situation.homeless() && situation.plotAffordable()) {
                utilities.put(AgentAction.BUY_PLOT, needs.housing() * (0.6 + 0.4 * (1 - personality.thrift())));
            }
        }
        if (situation.cashGap() > 0 && situation.desiredCash() > 0) {
            utilities.put(AgentAction.WITHDRAW_CASH,
                    0.5 * personality.cashAffinity() * situation.cashGap() / situation.desiredCash());
        }
        if (agent.cashOnHand() > 0) {
            double surplus = Math.max(0, -situation.cashGap()) / (double) agent.cashOnHand();
            utilities.put(AgentAction.DEPOSIT_CASH, 0.3 * surplus + 0.3 * needs.security());
            if (situation.hasTradePartner()) {
                utilities.put(AgentAction.PAY_IN_CASH, 0.25 * personality.cashAffinity());
            }
        }
        return utilities;
    }
}
