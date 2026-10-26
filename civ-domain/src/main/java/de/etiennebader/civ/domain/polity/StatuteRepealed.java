package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.Objects;

/**
 * Ein Gesetz wurde aufgehoben.
 */
public record StatuteRepealed(PolityId polity, StatuteId statute, Day occurredOn) implements PolityEvent {

    public StatuteRepealed {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(statute, "statute");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
