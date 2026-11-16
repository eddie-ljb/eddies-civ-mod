package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Parlament wurde gewählt.
 *
 * @param seats wie viele Sitze besetzt wurden
 */
public record ParliamentElected(PolityId polity, ElectionId election, int seats, Day occurredOn)
        implements PolityEvent {

    public ParliamentElected {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
