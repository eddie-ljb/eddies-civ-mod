package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine Aussage im Verfahren: Zeugenaussage, Einlassung oder Plädoyer.
 *
 * @param role     wer spricht, z. B. {@link Role#WITNESS}
 * @param tendency Wirkung von -2 (entlastet stark) bis 2 (belastet stark)
 */
public record Statement(ActorRef speaker, Role role, String text, int tendency, Day day) {

    public static final int MAX_TEXT = 600;

    public Statement {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(role, "role");
        text = Objects.requireNonNull(text, "text").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Leere Aussage");
        }
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        tendency = Math.max(-2, Math.min(2, tendency));
        Objects.requireNonNull(day, "day");
    }
}
