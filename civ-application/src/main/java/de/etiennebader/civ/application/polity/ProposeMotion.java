package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.Objects;

/**
 * Ein Bürger stellt einen Antrag: eine Regelung ändern, einen Paragraphen erlassen oder aufheben. Er stimmt selbst
 * dafür, wenn er stimmberechtigt ist (mit Parlament nur Abgeordnete); reicht das schon für die Mehrheit, gilt der
 * Antrag sofort als angenommen.
 */
public final class ProposeMotion {

    public record Command(PolityId polity, ActorRef citizen, Motion motion, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(motion, "motion");
            Objects.requireNonNull(day, "day");
        }
    }

    /** Die neue Abstimmung und ihr Stand nach der Stimme des Antragstellers. */
    public record Result(ProposalId proposal, VoteOutcome outcome) {
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public ProposeMotion(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @throws NotACitizenException wenn der Antragsteller kein Bürger ist
     */
    public Result execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        AmendLaw.requireCitizen(polity, command.citizen());
        ProposalId id = ProposalId.generate(ids);
        polity.openProposal(id, command.motion(), command.citizen(), command.day());
        if (Ballots.mayVote(polity, command.citizen())) {
            polity.vote(id, command.citizen(), true, command.day());
        }
        Proposal proposal = polity.proposals().stream().filter(open -> open.id().equals(id)).findFirst().orElseThrow();
        VoteOutcome outcome = Ballots.settle(polity, proposal, Ballots.evaluate(polity, proposal), ids, command.day());
        aggregates.commit(polity);
        return new Result(id, outcome);
    }
}
