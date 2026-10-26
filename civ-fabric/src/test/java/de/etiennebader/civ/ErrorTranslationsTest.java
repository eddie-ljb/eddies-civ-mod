package de.etiennebader.civ;

import de.etiennebader.civ.domain.shared.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stellt sicher, dass jeder fachliche Fehlercode in jeder Sprachdatei übersetzt ist
 * und keine verwaisten Fehlerschlüssel übrig bleiben.
 */
class ErrorTranslationsTest {

    private static final String ERROR_KEY_PREFIX = "eddies-civ-mod.error.";
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    @DisplayName("Deutsch übersetzt jeden Fehlercode")
    void germanCoversAllErrorCodes() throws IOException {
        assertEquals(expectedKeys(), errorKeysOf("de_de"));
    }

    @Test
    @DisplayName("Englisch übersetzt jeden Fehlercode")
    void englishCoversAllErrorCodes() throws IOException {
        assertEquals(expectedKeys(), errorKeysOf("en_us"));
    }

    private static Set<String> expectedKeys() {
        return Arrays.stream(ErrorCode.values())
                .map(ErrorCode::translationKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> errorKeysOf(String language) throws IOException {
        String path = "/assets/" + EddiesCivMod.MOD_ID + "/lang/" + language + ".json";
        try (InputStream stream = ErrorTranslationsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Sprachdatei fehlt: " + path);
            Matcher matcher = KEY.matcher(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            Set<String> keys = new TreeSet<>();
            while (matcher.find()) {
                if (matcher.group(1).startsWith(ERROR_KEY_PREFIX)) {
                    keys.add(matcher.group(1));
                }
            }
            return keys;
        }
    }
}
