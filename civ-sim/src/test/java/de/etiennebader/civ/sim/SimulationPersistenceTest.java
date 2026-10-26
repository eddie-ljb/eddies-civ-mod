package de.etiennebader.civ.sim;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.persistence.ChangeTracker;
import de.etiennebader.civ.application.persistence.StateDump;
import de.etiennebader.civ.application.persistence.WorldPersistence;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.persistence.store.SqliteStateStore;
import de.etiennebader.civ.sim.metrics.MetricsCsvWriter;
import de.etiennebader.civ.sim.scenario.Scenario;
import de.etiennebader.civ.sim.scenario.ScenarioLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abnahme der Persistenz: Der gesamte Zustand einer Simulation übersteht Speichern und Laden.
 */
class SimulationPersistenceTest {

    private static final Scenario SCENARIO = new ScenarioLoader().load("small").withDays(200);

    @Test
    @DisplayName("Nach 200 Tagen mit Autosave alle 30 Tage ist der geladene Zustand identisch")
    void stateSurvivesRestart() throws IOException {
        Path file = Files.createTempDirectory("civ-sim").resolve("civ.sqlite");
        String expected;
        SimulationResult result;
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            Simulation simulation = new Simulation(SCENARIO, DomainEventPublisher.NONE, Optional.of(new Autosave(store, 30)));
            result = simulation.run(day -> {
            });
            expected = StateDump.of(simulation.world().app().queries());
        }

        Repositories fresh = InMemoryRepositories.create();
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            new WorldPersistence(fresh, store, new ChangeTracker()).restore();
        }

        assertEquals(expected, StateDump.of(new WorldQueries(fresh)));
        assertEquals(7, result.saves().saves());
        assertEquals(result.totalEvents(), result.saves().events());
        assertTrue(expected.lines().count() > 50);
    }

    @Test
    @DisplayName("Speichern verändert den Verlauf nicht")
    void savingDoesNotChangeOutcome() throws IOException {
        Path file = Files.createTempDirectory("civ-sim").resolve("civ.sqlite");
        StringWriter withSave = new StringWriter();
        try (SqliteStateStore store = SqliteStateStore.open(file)) {
            new Simulation(SCENARIO, DomainEventPublisher.NONE, Optional.of(new Autosave(store, 10)))
                    .run(new MetricsCsvWriter(withSave));
        }
        StringWriter withoutSave = new StringWriter();
        new Simulation(SCENARIO, DomainEventPublisher.NONE).run(new MetricsCsvWriter(withoutSave));

        assertEquals(withoutSave.toString(), withSave.toString());
    }
}
