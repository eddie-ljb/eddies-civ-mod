package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Straße wurde angelegt oder neu vermessen.
 */
public record StreetLaid(PolityId polity, Street street, Day occurredOn) implements PolityEvent {

    public StreetLaid {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
