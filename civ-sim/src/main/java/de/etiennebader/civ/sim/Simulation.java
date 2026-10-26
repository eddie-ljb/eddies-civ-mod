package de.etiennebader.civ.sim;

import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.application.persistence.ChangeTracker;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.application.persistence.WorldPersistence;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.fiscal.LandTaxReport;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.sim.agent.Agent;
import de.etiennebader.civ.sim.agent.AgentAction;
import de.etiennebader.civ.sim.agent.AgentBrain;
import de.etiennebader.civ.sim.agent.AgentSituation;
import de.etiennebader.civ.sim.governance.MunicipalGovernment;
import de.etiennebader.civ.sim.invariant.InvariantChecker;
import de.etiennebader.civ.sim.metrics.DailyMetrics;
import de.etiennebader.civ.sim.metrics.EventCounter;
import de.etiennebader.civ.sim.metrics.MetricsCollector;
import de.etiennebader.civ.sim.scenario.Scenario;
import de.etiennebader.civ.sim.world.AgentRoutine;
import de.etiennebader.civ.sim.world.HousingMarket;
import de.etiennebader.civ.sim.world.SatisfactionModel;
import de.etiennebader.civ.sim.world.SimulationWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Spielt ein Szenario Tag für Tag durch. Vor Tag 1 erhält jeder Akteur ein zufälliges Startvermögen.
 * Ablauf eines Tages:
 * <ol>
 *     <li>Wohnungsmarkt erheben: Attraktivität und freie Grundstücke jeder Gemeinde</li>
 *     <li>Jeder Akteur in zufälliger Reihenfolge: Einkommen und Lebenshaltung, Lage beurteilen,
 *         nützlichste Handlung wählen und ausführen</li>
 *     <li>Jede Gemeindeverwaltung in Gründungsreihenfolge: Bauland, Gebiet, Steuern, Außenbeziehungen</li>
 *     <li>Tageswechsel: Grundsteuer einziehen</li>
 *     <li>Zufriedenheit fortschreiben, Kennzahlen erheben, Invarianten prüfen</li>
 * </ol>
 * Gleiches Szenario mit gleichem Seed ergibt exakt dieselben Kennzahlen und Events.
 *
 * <p>Mit {@link Autosave} wird der Zustand wie im Spiel regelmäßig gespeichert; das Speichern
 * beeinflusst den Verlauf nicht.
 */
public final class Simulation {

    private final SimulationWorld world;
    private final EventCounter eventCounter = new EventCounter();
    private final AgentBrain brain;
    private final AgentRoutine routine;
    private final SatisfactionModel satisfaction;
    private final MetricsCollector metrics;
    private final InvariantChecker invariants;
    private final Map<PolityId, LandTaxReport> lastTaxReports = new HashMap<>();
    private final Optional<Autosave> autosave;
    private final Optional<WorldPersistence> persistence;
    private SaveStatistics saveStatistics = SaveStatistics.NONE;

    /**
     * @param additionalPublisher erhält alle Domain Events zusätzlich, z. B. ein Event-Log
     */
    public Simulation(Scenario scenario, DomainEventPublisher additionalPublisher) {
        this(scenario, additionalPublisher, Optional.empty());
    }

    /**
     * @param additionalPublisher erhält alle Domain Events zusätzlich, z. B. ein Event-Log
     * @param autosave            speichert den Zustand regelmäßig und am Ende, falls angegeben
     */
    public Simulation(Scenario scenario, DomainEventPublisher additionalPublisher, Optional<Autosave> autosave) {
        Objects.requireNonNull(additionalPublisher, "additionalPublisher");
        this.autosave = Objects.requireNonNull(autosave, "autosave");
        ChangeTracker tracker = new ChangeTracker();
        DomainEventPublisher publisher = eventCounter.andThen(additionalPublisher);
        this.world = SimulationWorld.create(scenario, autosave.isPresent() ? publisher.andThen(tracker) : publisher);
        this.persistence = autosave.map(save -> new WorldPersistence(world.repositories(), save.store(), tracker));
        this.brain = new AgentBrain(scenario.behavior().decisionNoise());
        this.routine = new AgentRoutine(world);
        this.satisfaction = new SatisfactionModel(world);
        this.metrics = new MetricsCollector(world);
        this.invariants = new InvariantChecker(world);
        world.agents().forEach(routine::grantStartingWealth);
    }

    /**
     * Spielt alle Tage des Szenarios.
     *
     * @param metricsSink erhält die Kennzahlen jedes Tages
     * @throws de.etiennebader.civ.sim.invariant.InvariantViolationException beim ersten Tag mit verletzter Invariante
     */
    public SimulationResult run(Consumer<DailyMetrics> metricsSink) {
        Objects.requireNonNull(metricsSink, "metricsSink");
        DailyMetrics last = null;
        int days = world.scenario().days();
        for (long dayNumber = 1; dayNumber <= days; dayNumber++) {
            last = runDay(Day.of(dayNumber));
            metricsSink.accept(last);
            if (isSaveDay(dayNumber, days)) {
                save();
            }
        }
        return new SimulationResult(world.scenario(), last, world.rejections().total(), eventCounter.total(), saveStatistics);
    }

    private DailyMetrics runDay(Day day) {
        HousingMarket market = HousingMarket.survey(world);
        for (Agent agent : world.agentsInRandomOrder()) {
            routine.earnAndSpend(agent, day);
            AgentSituation situation = routine.assess(agent, market);
            AgentAction action = brain.decide(agent, situation, world.random());
            routine.perform(agent, action, market, situation, day);
        }
        for (MunicipalGovernment government : List.copyOf(world.governments())) {
            government.govern(day, Optional.ofNullable(lastTaxReports.get(government.polity())));
        }
        DayReport report = world.app().endOfDay().execute(day);
        report.landTaxReports().forEach(taxReport -> lastTaxReports.put(taxReport.polity(), taxReport));
        satisfaction.apply(report);

        DailyMetrics dailyMetrics = metrics.collect(report, world.rejections().takeToday(), eventCounter.takeToday());
        invariants.verify(day);
        return dailyMetrics;
    }

    private boolean isSaveDay(long day, int lastDay) {
        return autosave.map(save -> day % save.intervalDays() == 0 || day == lastDay).orElse(false);
    }

    private void save() {
        long start = System.nanoTime();
        FlushReport report = persistence.orElseThrow().flush();
        saveStatistics = saveStatistics.plus(System.nanoTime() - start, report.events());
    }

    /** Für Tests: Zugriff auf den Zustand. */
    SimulationWorld world() {
        return world;
    }
}
