package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Eine Polity wurde gegründet.
 */
public record PolityFounded(PolityId polity, PolityName name, ActorRef founder, BlockPosition seat, Day occurredOn)
        implements PolityEvent {

    public PolityFounded {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(seat, "seat");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
