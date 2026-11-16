package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Firma oder Zeitung wurde gegründet.
 */
public record OrganisationFounded(PolityId polity, PolityName name, PolityKind kind, ActorRef founder, Day occurredOn)
        implements PolityEvent {

    public OrganisationFounded {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
