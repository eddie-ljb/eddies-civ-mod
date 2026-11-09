package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.Objects;

/**
 * Eine Abstimmung über eine Rechtsänderung wurde eröffnet.
 */
public record ProposalOpened(PolityId polity, ProposalId proposal, String parameter, ActorRef proposer, Day occurredOn)
        implements PolityEvent {

    public ProposalOpened {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
