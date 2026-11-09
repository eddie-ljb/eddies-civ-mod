package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.Objects;

/**
 * Ein Bürger beantragt eine Rechtsänderung. Er stimmt selbst dafür; reicht das schon für die Mehrheit
 * (etwa als einziger Bürger), gilt die Änderung sofort.
 */
public final class ProposeLawChange {

    public record Command(PolityId polity, ActorRef citizen, ParameterValue<?> change, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(change, "change");
            Objects.requireNonNull(day, "day");
        }
    }

    /** Die neue Abstimmung und ihr Stand nach der Stimme des Antragstellers. */
    public record Result(ProposalId proposal, VoteOutcome outcome) {
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public ProposeLawChange(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public Result execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        AmendLaw.requireCitizen(polity, command.citizen());
        ProposalId id = ProposalId.generate(ids);
        polity.openProposal(id, command.change(), command.citizen(), command.day());
        polity.vote(id, command.citizen(), true, command.day());
        Proposal proposal = proposalOf(polity, id);
        VoteOutcome outcome = Ballots.settle(polity, proposal, Ballots.evaluate(polity, proposal), ids, command.day());
        aggregates.commit(polity);
        return new Result(id, outcome);
    }

    static Proposal proposalOf(Polity polity, ProposalId id) {
        return polity.proposals().stream().filter(open -> open.id().equals(id)).findFirst().orElseThrow();
    }
}
