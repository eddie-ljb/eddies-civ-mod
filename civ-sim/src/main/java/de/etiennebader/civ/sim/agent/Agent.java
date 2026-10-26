package de.etiennebader.civ.sim.agent;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein simulierter Spieler oder Villager.
 *
 * <p>Das Bargeld in der Tasche führt die Simulation selbst, denn auch im Spiel kennt der Kern
 * nur die Summe des umlaufenden Bargelds, nicht wer es hält. Die Zugehörigkeit zu einer Gemeinde
 * ist ein Cache für schnelle Entscheidungen; die Invariantenprüfung gleicht ihn täglich mit dem
 * Kern ab.
 */
public final class Agent {

    /** Zufriedenheit eines Akteurs, der neu in eine Gemeinde kommt. */
    public static final double INITIAL_SATISFACTION = 0.7;

    private final ActorRef actor;
    private final AgentKind kind;
    private final Personality personality;
    private final Needs needs = new Needs();
    private PolityId home;
    private double satisfaction = INITIAL_SATISFACTION;
    private long cashOnHand;

    public Agent(ActorRef actor, AgentKind kind, Personality personality) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.personality = Objects.requireNonNull(personality, "personality");
    }

    public ActorRef actor() {
        return actor;
    }

    public AgentKind kind() {
        return kind;
    }

    public Personality personality() {
        return personality;
    }

    public Needs needs() {
        return needs;
    }

    public Optional<PolityId> home() {
        return Optional.ofNullable(home);
    }

    public boolean isMember() {
        return home != null;
    }

    public void moveTo(PolityId polity) {
        this.home = Objects.requireNonNull(polity, "polity");
        this.satisfaction = INITIAL_SATISFACTION;
    }

    public void becomeHomeless() {
        this.home = null;
    }

    public double satisfaction() {
        return satisfaction;
    }

    public void adjustSatisfaction(double delta) {
        satisfaction = Math.max(0, Math.min(1, satisfaction + delta));
    }

    public long cashOnHand() {
        return cashOnHand;
    }

    public void receiveCash(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Betrag darf nicht negativ sein: " + amount);
        }
        cashOnHand = Math.addExact(cashOnHand, amount);
    }

    public void spendCash(long amount) {
        if (amount < 0 || amount > cashOnHand) {
            throw new IllegalArgumentException("Ungültiger Bargeldbetrag " + amount + " bei " + cashOnHand);
        }
        cashOnHand -= amount;
    }

    /** Persönliche Rücklage: sparsame Akteure wollen mehr auf dem Konto haben. */
    public long savingsTarget(long baseTarget) {
        return Math.round(baseTarget * (0.5 + personality.thrift()));
    }

    /** Tägliches Einkommen nach Produktivität. */
    public long dailyIncome(long baseIncome) {
        return Math.round(baseIncome * personality.productivity());
    }

    @Override
    public String toString() {
        return kind + " " + actor.uuid();
    }
}
