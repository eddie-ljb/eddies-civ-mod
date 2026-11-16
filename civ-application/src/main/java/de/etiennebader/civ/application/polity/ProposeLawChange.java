package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.polity.Motion;
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

    private final ProposeMotion proposeMotion;

    public ProposeLawChange(Aggregates aggregates, IdGenerator ids) {
        this.proposeMotion = new ProposeMotion(aggregates, ids);
    }

    public Result execute(Command command) {
        ProposeMotion.Result result = proposeMotion.execute(new ProposeMotion.Command(command.polity(),
                command.citizen(), new Motion.Change(command.change()), command.day()));
        return new Result(result.proposal(), result.outcome());
    }

    static Proposal proposalOf(Polity polity, ProposalId id) {
        return polity.proposals().stream().filter(open -> open.id().equals(id)).findFirst().orElseThrow();
    }
}
