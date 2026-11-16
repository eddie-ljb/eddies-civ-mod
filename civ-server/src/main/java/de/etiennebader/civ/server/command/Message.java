package de.etiennebader.civ.server.command;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ein übersetzbarer Text: Schlüssel aus den Sprachdateien plus Argumente.
 *
 * <p>Argumente sind fertige Zeichenketten oder selbst übersetzbare Texte, z. B. der Name einer
 * Siedlungsstufe. So entscheidet erst der Client über die Sprache.
 */
public record Message(String key, List<Object> args) {

    public Message {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Schlüssel darf nicht leer sein");
        }
        args = List.copyOf(Objects.requireNonNull(args, "args"));
        for (Object arg : args) {
            if (!(arg instanceof String) && !(arg instanceof Message)) {
                throw new IllegalArgumentException("Argumente müssen String oder Message sein: " + arg);
            }
        }
    }

    public static Message of(MessageKey key, Object... args) {
        return of(key.key(), args);
    }

    /** Zahlen werden zu Zeichenketten; Strings und Messages bleiben, wie sie sind. */
    public static Message of(String key, Object... args) {
        return new Message(key, Arrays.stream(args).map(Message::normalize).toList());
    }

    /**
     * Ein anklickbarer Knopf im Chat, z. B. {@code [Annehmen]}; im Spiel führt ein Klick den Befehl aus, die Webseite
     * zeigt nur die Beschriftung.
     */
    public static Message action(Message label, String command) {
        return of(MessageKey.ACTION, label, command);
    }

    /** Ob dies ein Knopf aus {@link #action(Message, String)} ist. */
    public boolean isAction() {
        return key.equals(MessageKey.ACTION.key()) && args.size() == 2;
    }

    private static Object normalize(Object arg) {
        Objects.requireNonNull(arg, "arg");
        return arg instanceof Number number ? number.toString() : arg;
    }
}
