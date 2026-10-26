package de.etiennebader.civ.sim;

import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.sim.invariant.InvariantChecker;
import de.etiennebader.civ.sim.invariant.InvariantViolationException;
import de.etiennebader.civ.sim.metrics.DailyMetrics;
import de.etiennebader.civ.sim.metrics.EventLogWriter;
import de.etiennebader.civ.sim.metrics.MetricsCsvWriter;
import de.etiennebader.civ.sim.scenario.Scenario;
import de.etiennebader.civ.sim.scenario.ScenarioLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationTest {

    private static final Scenario SMALL = new ScenarioLoader().load("small");

    @Test
    @DisplayName("Gleicher Seed → byte-identische Kennzahlen und Event-Logs")
    void sameSeedIsDeterministic() {
        Output first = run(SMALL.withDays(200));
        Output second = run(SMALL.withDays(200));

        assertEquals(first.metrics(), second.metrics());
        assertEquals(first.events(), second.events());
        assertTrue(first.events().lines().count() > 1000);
    }

    @Test
    @DisplayName("Anderer Seed → anderer Verlauf")
    void differentSeedDiffers() {
        assertNotEquals(run(SMALL.withDays(100)).metrics(), run(SMALL.withDays(100).withSeed(7)).metrics());
    }

    @Test
    @DisplayName("Ein Jahr im kleinen Szenario nutzt alle Phase-0-Mechaniken und hält alle Invarianten")
    void yearExercisesAllMechanics() {
        List<DailyMetrics> days = new ArrayList<>();
        SimulationResult result = new Simulation(SMALL, DomainEventPublisher.NONE).run(days::add);

        assertEquals(365, days.size());
        assertEquals(365, result.finalDay().day());
        assertTrue(result.finalDay().polities() >= 2, "mehrere Gemeinden");
        assertTrue(days.stream().anyMatch(day -> day.privatePlots() > 0), "Grundstücke verkauft");
        assertTrue(days.stream().anyMatch(day -> day.taxCollected() > 0), "Grundsteuer eingezogen");
        assertTrue(days.stream().anyMatch(day -> day.cash() > 0), "Bargeld im Umlauf");
        assertTrue(days.stream().anyMatch(day -> day.outposts() > 0), "Außenposten");
        days.forEach(day -> assertEquals(day.moneySupply(), day.deposits() + day.cash()));
    }

    @Test
    @DisplayName("Invariantenprüfung erkennt Bargeld, das außerhalb des Kerns entsteht")
    void detectsCashCreatedOutsideTheCore() {
        Simulation simulation = new Simulation(SMALL.withDays(5), DomainEventPublisher.NONE);
        simulation.run(day -> {
        });

        simulation.world().agents().getFirst().receiveCash(1);

        InvariantViolationException violation = assertThrows(InvariantViolationException.class,
                () -> new InvariantChecker(simulation.world()).verify(Day.of(6)));
        assertTrue(violation.violations().getFirst().startsWith("Bargeld in Taschen"));
    }

    private static Output run(Scenario scenario) {
        StringWriter metrics = new StringWriter();
        StringWriter events = new StringWriter();
        new Simulation(scenario, new EventLogWriter(events)).run(new MetricsCsvWriter(metrics));
        return new Output(metrics.toString(), events.toString());
    }

    private record Output(String metrics, String events) {
    }
}
