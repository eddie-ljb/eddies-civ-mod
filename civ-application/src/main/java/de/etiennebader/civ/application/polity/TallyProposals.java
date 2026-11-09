package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zählt am Tagesende alle Abstimmungen aus, deren Frist ({@link StandardParameters#VOTE_DAYS}) abgelaufen ist.
 */
public final class TallyProposals {

    /** Eine ausgezählte Abstimmung. */
    public record Tallied(PolityId polity, Proposal proposal, VoteOutcome outcome) {
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public TallyProposals(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public List<Tallied> execute(Day day) {
        Objects.requireNonNull(day, "day");
        List<Tallied> tallied = new ArrayList<>();
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            long voteDays = polity.laws().valueOf(StandardParameters.VOTE_DAYS);
            boolean changed = false;
            for (Proposal proposal : polity.proposals()) {
                if (day.isBefore(proposal.openedOn().plus(voteDays))) {
                    continue;
                }
                VoteOutcome outcome = Ballots.settle(polity, proposal, Ballots.atDeadline(proposal), ids, day);
                tallied.add(new Tallied(polity.id(), proposal, outcome));
                changed = true;
            }
            if (changed) {
                aggregates.commit(polity);
            }
        }
        return tallied;
    }
}
