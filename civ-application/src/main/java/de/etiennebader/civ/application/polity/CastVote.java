package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.Objects;

/**
 * Ein Bürger stimmt ab oder ändert seine Stimme. Steht danach die Mehrheit fest, wird sofort entschieden.
 */
public final class CastVote {

    public record Command(PolityId polity, ProposalId proposal, ActorRef citizen, boolean inFavour, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public CastVote(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public VoteOutcome execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        AmendLaw.requireCitizen(polity, command.citizen());
        polity.vote(command.proposal(), command.citizen(), command.inFavour(), command.day());
        Proposal proposal = ProposeLawChange.proposalOf(polity, command.proposal());
        VoteOutcome outcome = Ballots.settle(polity, proposal, Ballots.evaluate(polity, proposal), ids, command.day());
        aggregates.commit(polity);
        return outcome;
    }
}
