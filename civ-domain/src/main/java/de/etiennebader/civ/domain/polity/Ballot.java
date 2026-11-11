package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;

import java.util.Objects;

/**
 * Eine Stimme bei einer Wahl.
 */
public record Ballot(ActorRef voter, ActorRef candidate) {

    public Ballot {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(candidate, "candidate");
    }
}
