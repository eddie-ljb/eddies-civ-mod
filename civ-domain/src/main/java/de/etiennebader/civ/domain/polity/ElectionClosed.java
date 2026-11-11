package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Wahl wurde ausgezählt; {@code decided} ist falsch, wenn niemand kandidiert hat.
 */
public record ElectionClosed(PolityId polity, ElectionId election, Office office, boolean decided, Day occurredOn)
        implements PolityEvent {

    public ElectionClosed {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
