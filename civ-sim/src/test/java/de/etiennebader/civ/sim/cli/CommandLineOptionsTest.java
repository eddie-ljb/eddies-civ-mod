package de.etiennebader.civ.sim.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineOptionsTest {

    @Test
    @DisplayName("Ohne Argumente gelten die Standardwerte")
    void defaults() {
        CommandLineOptions options = CommandLineOptions.parse();

        assertEquals("medium", options.scenario());
        assertEquals(OptionalLong.empty(), options.seed());
        assertEquals(OptionalInt.empty(), options.days());
        assertEquals(Path.of("build", "simulation"), options.output());
        assertFalse(options.eventLog());
    }

    @Test
    @DisplayName("Alle Optionen werden gelesen")
    void parsesAllOptions() {
        CommandLineOptions options = CommandLineOptions.parse(
                "--scenario", "small", "--seed", "-3", "--days", "50", "--out", "ziel", "--event-log");

        assertEquals("small", options.scenario());
        assertEquals(OptionalLong.of(-3), options.seed());
        assertEquals(OptionalInt.of(50), options.days());
        assertEquals(Path.of("ziel"), options.output());
        assertTrue(options.eventLog());
    }

    @Test
    @DisplayName("Fehlerhafte Kommandozeilen werden abgelehnt")
    void rejectsInvalidInput() {
        assertThrows(UsageException.class, () -> CommandLineOptions.parse("--unbekannt"));
        assertThrows(UsageException.class, () -> CommandLineOptions.parse("--days"));
        assertThrows(UsageException.class, () -> CommandLineOptions.parse("--days", "0"));
        assertThrows(UsageException.class, () -> CommandLineOptions.parse("--seed", "abc"));
        assertThrows(UsageException.class, () -> CommandLineOptions.parse("--scenario", "--event-log"));
    }
}
