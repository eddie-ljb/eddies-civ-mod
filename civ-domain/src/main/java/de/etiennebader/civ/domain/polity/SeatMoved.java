package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;
import java.util.Optional;

/**
 * Der Sitz einer Polity wurde verlegt.
 */
public record SeatMoved(PolityId polity, Optional<BlockPosition> from, BlockPosition to, Day occurredOn)
        implements PolityEvent {

    public SeatMoved {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
