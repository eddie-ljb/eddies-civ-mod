package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Beweismittel.
 *
 * @param kind   Art, z. B. {@code buch}, {@code gegenstand}, {@code vertrag}, {@code artikel}, {@code text}
 * @param weight Beweiskraft 1 (Indiz) bis 3 (Urkunde)
 */
public record Evidence(int number, ActorRef submittedBy, Side side, String kind, String title, String content,
                       int weight, Day day) {

    public static final int MAX_TEXT = 2000;

    public Evidence {
        Objects.requireNonNull(submittedBy, "submittedBy");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(kind, "kind");
        title = Objects.requireNonNull(title, "title").strip();
        content = Objects.requireNonNull(content, "content").strip();
        if (content.length() > MAX_TEXT) {
            content = content.substring(0, MAX_TEXT);
        }
        weight = Math.max(1, Math.min(3, weight));
        Objects.requireNonNull(day, "day");
    }
}
