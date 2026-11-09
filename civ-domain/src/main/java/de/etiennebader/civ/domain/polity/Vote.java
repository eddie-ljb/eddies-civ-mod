package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;

import java.util.Objects;

/**
 * Eine Stimme in einer Abstimmung.
 */
public record Vote(ActorRef voter, boolean inFavour) {

    public Vote {
        Objects.requireNonNull(voter, "voter");
    }
}
