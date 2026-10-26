package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Akteur wurde als Mitglied aufgenommen.
 */
public record MemberAdmitted(PolityId polity, ActorRef member, MembershipType type, Day occurredOn) implements PolityEvent {

    public MemberAdmitted {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
