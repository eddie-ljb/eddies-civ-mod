package de.etiennebader.civ.server.web;

import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Übersetzt {@link Message}s für die Webseite mit denselben Sprachdateien wie das Spiel. Fehlt eine Übersetzung,
 * erscheint der Schlüssel.
 */
public final class Translator {

    static final String RESOURCE = "/assets/eddies-civ-mod/lang/%s.json";

    private final Map<String, String> entries;

    public Translator(Map<String, String> entries) {
        this.entries = Map.copyOf(entries);
    }

    /** Lädt eine Sprachdatei aus dem Klassenpfad, z. B. {@code de_de}; fehlt sie, bleibt die Übersetzung leer. */
    public static Translator load(String language) {
        try (InputStream in = Translator.class.getResourceAsStream(RESOURCE.formatted(language))) {
            if (in == null) {
                return new Translator(Map.of());
            }
            return new Translator(Json.parseFlat(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException e) {
            return new Translator(Map.of());
        }
    }

    public String translate(Message message) {
        Objects.requireNonNull(message, "message");
        Object[] args = message.args().stream().map(arg -> arg instanceof Message nested ? translate(nested) : arg).toArray();
        String pattern = entries.get(message.key());
        if (pattern == null) {
            return message.key();
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException e) {
            return pattern;
        }
    }

    public String translate(Reply reply) {
        return reply.lines().stream().map(this::translate).collect(Collectors.joining("\n"));
    }
}
