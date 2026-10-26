package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Mitglied ist ausgetreten.
 */
public record MemberLeft(PolityId polity, ActorRef member, Day occurredOn) implements PolityEvent {

    public MemberLeft {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
