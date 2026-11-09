package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.Objects;

/**
 * Eine Abstimmung ist beendet: angenommen oder abgelehnt.
 */
public record ProposalClosed(PolityId polity, ProposalId proposal, boolean accepted, Day occurredOn) implements PolityEvent {

    public ProposalClosed {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
