package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.Objects;

/**
 * Ein Bürger hat abgestimmt.
 */
public record VoteCast(PolityId polity, ProposalId proposal, ActorRef voter, boolean inFavour, Day occurredOn)
        implements PolityEvent {

    public VoteCast {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
