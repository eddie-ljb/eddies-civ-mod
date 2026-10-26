package de.etiennebader.civ.sim.world;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.AdjacencyClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimLimitPolicy;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import de.etiennebader.civ.domain.territory.OutpostClaimPolicy;
import de.etiennebader.civ.sim.agent.Agent;
import de.etiennebader.civ.sim.agent.AgentKind;
import de.etiennebader.civ.sim.agent.Personality;
import de.etiennebader.civ.sim.governance.MunicipalGovernment;
import de.etiennebader.civ.sim.scenario.Scenario;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Der gesamte Zustand einer Simulation: die verdrahtete Application mit In-Memory-Adaptern,
 * alle Akteure, die Verwaltungen der Gemeinden und die Zufallsquellen.
 *
 * <p>Zufall für Entscheidungen und Zufall für IDs kommen aus zwei getrennten, aus dem Seed
 * abgeleiteten Quellen. So verschiebt eine zusätzliche Entscheidung nicht alle späteren IDs.
 */
public final class SimulationWorld {

    public static final CurrencyId CURRENCY = CurrencyId.EMERALD;

    private final Scenario scenario;
    private final Repositories repositories;
    private final CivApplication app;
    private final RandomGenerator random;
    private final List<Agent> agents;
    private final Map<ActorRef, Agent> agentsByActor = new LinkedHashMap<>();
    private final Map<PolityId, MunicipalGovernment> governments = new LinkedHashMap<>();
    private final RejectionCounter rejections = new RejectionCounter();
    private final PolityNames polityNames;

    private SimulationWorld(Scenario scenario, Repositories repositories, CivApplication app, RandomGenerator random,
                            List<Agent> agents) {
        this.scenario = scenario;
        this.repositories = repositories;
        this.app = app;
        this.random = random;
        this.agents = List.copyOf(agents);
        this.agents.forEach(agent -> agentsByActor.put(agent.actor(), agent));
        this.polityNames = new PolityNames(random);
    }

    public static SimulationWorld create(Scenario scenario, DomainEventPublisher publisher) {
        Objects.requireNonNull(scenario, "scenario");
        SplittableRandom root = new SplittableRandom(scenario.seed());
        RandomGenerator random = root.split();
        IdGenerator ids = new SeededIdGenerator(root.nextLong());
        Repositories repositories = InMemoryRepositories.create();
        CivApplication app = new CivApplication(repositories, ids, claimPolicyOf(scenario), publisher);

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < scenario.population().players(); i++) {
            agents.add(new Agent(new PlayerRef(PlayerId.generate(ids)), AgentKind.PLAYER,
                    Personality.random(AgentKind.PLAYER, random, scenario.economy().incomeSpread())));
        }
        for (int i = 0; i < scenario.population().villagers(); i++) {
            agents.add(new Agent(new PersonRef(PersonId.generate(ids)), AgentKind.VILLAGER,
                    Personality.random(AgentKind.VILLAGER, random, scenario.economy().incomeSpread())));
        }
        return new SimulationWorld(scenario, repositories, app, random, agents);
    }

    /** Die Claim-Regeln des simulierten Servers. */
    static ClaimPolicy claimPolicyOf(Scenario scenario) {
        List<ClaimPolicy> policies = new ArrayList<>();
        if (scenario.territory().requireAdjacency()) {
            policies.add(new AdjacencyClaimPolicy());
        }
        policies.add(new OutpostClaimPolicy());
        policies.add(new ClaimLimitPolicy(scenario.territory().claimBase(), scenario.territory().claimPerMember()));
        return new CompositeClaimPolicy(policies);
    }

    public Scenario scenario() {
        return scenario;
    }

    /** Der Arbeitsstand im Speicher, z. B. zum Speichern über {@code WorldPersistence}. */
    public Repositories repositories() {
        return repositories;
    }

    public CivApplication app() {
        return app;
    }

    public RandomGenerator random() {
        return random;
    }

    /** Alle Akteure in fester Reihenfolge: zuerst Spieler, dann Villager. */
    public List<Agent> agents() {
        return agents;
    }

    public Agent agent(ActorRef actor) {
        Agent agent = agentsByActor.get(actor);
        if (agent == null) {
            throw new IllegalArgumentException("Unbekannter Akteur: " + actor);
        }
        return agent;
    }

    /** Die Akteure in einer für heute zufällig gemischten, aber reproduzierbaren Reihenfolge. */
    public List<Agent> agentsInRandomOrder() {
        List<Agent> shuffled = new ArrayList<>(agents);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            Collections.swap(shuffled, i, random.nextInt(i + 1));
        }
        return shuffled;
    }

    public MunicipalGovernment government(PolityId polity) {
        MunicipalGovernment government = governments.get(polity);
        if (government == null) {
            throw new IllegalStateException("Keine Verwaltung für " + polity);
        }
        return government;
    }

    /** Alle Verwaltungen in Gründungsreihenfolge. */
    public Collection<MunicipalGovernment> governments() {
        return Collections.unmodifiableCollection(governments.values());
    }

    void establishGovernment(PolityId polity) {
        governments.put(polity, new MunicipalGovernment(polity, app, scenario, random, rejections));
    }

    public RejectionCounter rejections() {
        return rejections;
    }

    String nextPolityName() {
        return polityNames.next();
    }
}
