package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Polity wurde aufgelöst.
 */
public record PolityDissolved(PolityId polity, PolityName name, Day occurredOn) implements PolityEvent {

    public PolityDissolved {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
