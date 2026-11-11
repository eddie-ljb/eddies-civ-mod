package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Jemand hat bei einer Wahl abgestimmt. Für wen, bleibt geheim und steht nicht im Ereignis.
 */
public record BallotCast(PolityId polity, ElectionId election, ActorRef voter, Day occurredOn)
        implements PolityEvent {

    public BallotCast {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
