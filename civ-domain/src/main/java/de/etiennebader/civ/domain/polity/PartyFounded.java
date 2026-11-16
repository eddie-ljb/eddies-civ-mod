package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Partei wurde gegründet.
 */
public record PartyFounded(PolityId polity, PolityName name, ActorRef founder, PartyProfile profile, Day occurredOn)
        implements PolityEvent {

    public PartyFounded {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
