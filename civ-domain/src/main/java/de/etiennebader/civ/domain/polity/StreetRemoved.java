package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Straße wurde aufgehoben.
 */
public record StreetRemoved(PolityId polity, String name, Day occurredOn) implements PolityEvent {

    public StreetRemoved {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
