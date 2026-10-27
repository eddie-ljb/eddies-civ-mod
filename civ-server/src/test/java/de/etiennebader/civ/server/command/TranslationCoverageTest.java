package de.etiennebader.civ.server.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Sprachdateien liegen in {@code civ-fabric}; dieser Test prüft sie gegen alle Schlüssel, die
 * Befehle und Fehlermeldungen verwenden. Fehlende Schlüssel würden im Spiel roh angezeigt.
 */
class TranslationCoverageTest {

    private static final Path LANG_DIR = Path.of("../civ-fabric/src/main/resources/assets/eddies-civ-mod/lang");
    private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE);

    @Test
    @DisplayName("Deutsch übersetzt alle Schlüssel")
    void germanCoversAllKeys() throws IOException {
        assertCoversAllKeys("de_de");
    }

    @Test
    @DisplayName("Englisch übersetzt alle Schlüssel")
    void englishCoversAllKeys() throws IOException {
        assertCoversAllKeys("en_us");
    }

    private static void assertCoversAllKeys(String language) throws IOException {
        Set<String> present = keysOf(LANG_DIR.resolve(language + ".json"));
        List<String> missing = new ArrayList<>(TranslationKeys.all());
        missing.removeAll(present);
        assertTrue(missing.isEmpty(), () -> language + " fehlt: " + missing);
    }

    private static Set<String> keysOf(Path file) throws IOException {
        Matcher matcher = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
