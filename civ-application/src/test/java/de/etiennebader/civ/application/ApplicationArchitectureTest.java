package de.etiennebader.civ.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Application-Schicht kennt weder Minecraft noch die Simulation noch eine Speichertechnik.
 */
class ApplicationArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final List<String> FORBIDDEN = List.of(
            "import net.minecraft.", "import net.fabricmc.", "import com.mojang.", "import de.etiennebader.civ.sim.",
            "import de.etiennebader.civ.persistence.", "import java.sql.");

    @Test
    @DisplayName("Keine Imports aus Minecraft, Fabric, Simulation, Persistenz oder JDBC")
    void hasNoForbiddenImports() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            violations = files.filter(file -> file.toString().endsWith(".java"))
                    .flatMap(ApplicationArchitectureTest::forbiddenImportsOf)
                    .toList();
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static Stream<String> forbiddenImportsOf(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> FORBIDDEN.stream().anyMatch(line::startsWith))
                    .map(line -> source + ": " + line);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
