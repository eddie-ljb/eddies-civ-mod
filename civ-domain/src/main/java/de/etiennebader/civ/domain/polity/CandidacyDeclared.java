package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Bürger kandidiert bei einer Wahl.
 */
public record CandidacyDeclared(PolityId polity, ElectionId election, ActorRef candidate, Platform platform,
                                Day occurredOn)
        implements PolityEvent {

    public CandidacyDeclared {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
