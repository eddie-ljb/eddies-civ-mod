package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.Objects;

/**
 * Ein Gesetz ist in Kraft getreten.
 */
public record StatuteEnacted(PolityId polity, StatuteId statute, Day occurredOn) implements PolityEvent {

    public StatuteEnacted {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(statute, "statute");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
