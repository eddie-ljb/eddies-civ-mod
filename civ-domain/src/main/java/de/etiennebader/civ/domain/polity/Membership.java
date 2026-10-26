package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Die Mitgliedschaft eines Akteurs in einer Polity.
 */
public record Membership(ActorRef member, MembershipType type, Day since) {

    public Membership {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(since, "since");
    }
}
