package de.etiennebader.civ.server.command;

import java.util.List;
import java.util.Objects;

/**
 * Antwort eines Befehls: eine oder mehrere Zeilen und ob der Befehl gelungen ist.
 */
public record Reply(boolean success, List<Message> lines) {

    public Reply {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Eine Antwort braucht mindestens eine Zeile");
        }
    }

    public static Reply success(Message... lines) {
        return new Reply(true, List.of(lines));
    }

    public static Reply success(List<Message> lines) {
        return new Reply(true, lines);
    }

    public static Reply failure(Message line) {
        return new Reply(false, List.of(line));
    }
}
