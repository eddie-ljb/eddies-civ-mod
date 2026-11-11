package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Wahl für ein Amt wurde ausgerufen.
 */
public record ElectionCalled(PolityId polity, ElectionId election, Office office, Day closesOn, Day occurredOn)
        implements PolityEvent {

    public ElectionCalled {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(closesOn, "closesOn");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
