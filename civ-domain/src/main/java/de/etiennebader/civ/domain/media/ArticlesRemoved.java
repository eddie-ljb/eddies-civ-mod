package de.etiennebader.civ.domain.media;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/** Die Artikel einer aufgelösten Zeitung wurden entfernt. */
public record ArticlesRemoved(PolityId newspaper, Day occurredOn) implements PressEvent {

    public ArticlesRemoved {
        Objects.requireNonNull(newspaper, "newspaper");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
