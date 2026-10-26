package de.etiennebader.civ.sim.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioLoaderTest {

    private final ScenarioLoader loader = new ScenarioLoader();

    @Test
    @DisplayName("Mitgelieferte Szenarien laden und erben die Standardwerte")
    void loadsBuiltInScenarios() {
        Scenario small = loader.load("small");

        assertEquals("small", small.name());
        assertEquals(365, small.days());
        assertEquals(20, small.population().total());
        assertEquals(42, small.seed());
        assertTrue(small.territory().requireAdjacency());
        assertEquals(1000, loader.load("medium").days());
        assertEquals(2000, loader.load("stress").population().total());
    }

    @Test
    @DisplayName("Szenario aus einer Datei, Name ohne Endung")
    void loadsScenarioFromFile() throws IOException {
        Path file = Files.createTempFile("insel", ".properties");
        Files.writeString(file, "days=10\npopulation.players=1\npopulation.villagers=2\n", StandardCharsets.UTF_8);

        Scenario scenario = loader.load(file.toString());

        assertEquals(10, scenario.days());
        assertEquals(3, scenario.population().total());
        assertTrue(scenario.name().startsWith("insel"));
        Files.delete(file);
    }

    @Test
    @DisplayName("Alle Fehler werden auf einmal gemeldet: Tippfehler, Typ, Wertebereich")
    void reportsAllProblems() {
        Properties properties = new Properties();
        properties.setProperty("population.player", "5");
        properties.setProperty("days", "viele");
        properties.setProperty("behavior.decision.noise", "1.5");
        properties.setProperty("territory.require.adjacency", "ja");

        InvalidScenarioException exception = assertThrows(InvalidScenarioException.class,
                () -> loader.fromProperties("kaputt", properties));

        assertEquals(4, exception.problems().size());
        assertTrue(exception.getMessage().contains("unbekannter Schlüssel 'population.player'"));
    }

    @Test
    @DisplayName("Kleinste Grundstücksgröße über der größten ist ungültig")
    void rejectsInvertedPlotSizes() {
        Properties properties = new Properties();
        properties.setProperty("land.plot.size.min", "12");
        properties.setProperty("land.plot.size.max", "8");

        assertThrows(InvalidScenarioException.class, () -> loader.fromProperties("x", properties));
    }

    @Test
    @DisplayName("Unbekannter Name und fehlende Datei werden abgelehnt")
    void rejectsUnknownScenario() {
        assertThrows(InvalidScenarioException.class, () -> loader.load("gibt-es-nicht"));
    }

    @Test
    @DisplayName("Seed und Tage lassen sich überschreiben")
    void overridesSeedAndDays() {
        Scenario scenario = loader.load("small").withSeed(7).withDays(3);

        assertEquals(7, scenario.seed());
        assertEquals(3, scenario.days());
        assertFalse(scenario.population().total() == 0);
    }
}
