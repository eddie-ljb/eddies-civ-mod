package de.etiennebader.civ.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationMainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    @DisplayName("Lauf schreibt metrics.csv und events.log ins Ausgabeverzeichnis")
    void writesOutputFiles() throws IOException {
        Path directory = Files.createTempDirectory("civ-sim");

        int exitCode = run("--scenario", "small", "--days", "30", "--out", directory.toString(), "--event-log");

        assertEquals(SimulationMain.SUCCESS, exitCode, err.toString(StandardCharsets.UTF_8));
        List<String> metrics = Files.readAllLines(directory.resolve(SimulationMain.METRICS_FILE));
        assertEquals(31, metrics.size());
        assertTrue(metrics.get(30).startsWith("30,"));
        assertTrue(Files.size(directory.resolve(SimulationMain.EVENT_LOG_FILE)) > 0);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("alle Invarianten erfüllt"));
    }

    @Test
    @DisplayName("Hilfe → Exit-Code 0, falsche Option oder falsches Szenario → 2")
    void exitCodes() {
        assertEquals(SimulationMain.SUCCESS, run("--help"));
        assertEquals(SimulationMain.INVALID_INPUT, run("--falsch"));
        assertEquals(SimulationMain.INVALID_INPUT, run("--scenario", "gibt-es-nicht"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unbekannte Option"));
    }

    private int run(String... args) {
        return SimulationMain.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }
}
