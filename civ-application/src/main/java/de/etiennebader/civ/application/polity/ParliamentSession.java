package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.polity.ProposalAlreadyOpenException;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sitzung des Parlaments am Ende jedes Sitzungstags ({@link StandardParameters#SESSION_DAYS}): Dorfbewohner mit Sitz
 * stellen Anträge und stimmen ab; danach zählt {@link TallyProposals} fällige Anträge aus.
 *
 * <ul>
 *   <li>An jedem dritten Tag (fest je Parlament und Tag gewürfelt) stellt ein Dorfbewohner-Abgeordneter einen Antrag,
 *       wenn ihm etwas wichtig genug ist und kein anderer Antrag eines Dorfbewohners offen ist.</li>
 *   <li>Zu jedem offenen Antrag stimmt jeder Dorfbewohner-Abgeordnete ab, der noch nicht abgestimmt hat (oder enthält
 *       sich).</li>
 *   <li>Steht danach die Mehrheit fest, wird sofort entschieden.</li>
 * </ul>
 * Spieler mit Sitz stimmen selbst ab; was sie bis zur Frist nicht tun, zählt als Enthaltung.
 */
public final class ParliamentSession {

    /** Was im Parlament geschehen ist. */
    public sealed interface Outcome {

        PolityId polity();
    }

    /** Das Parlament hat getagt. */
    public record Session(PolityId polity, int agenda) implements Outcome {
    }

    /** Ein Dorfbewohner hat einen Antrag gestellt. */
    public record Tabled(PolityId polity, PersonRef member, Motion motion) implements Outcome {
    }

    /** Dorfbewohner haben über einen Antrag abgestimmt. */
    public record Voted(PolityId polity, Proposal proposal, int inFavour, int against) implements Outcome {
    }

    /** Ein Antrag wurde entschieden, weil die Mehrheit feststeht. */
    public record Decided(PolityId polity, Proposal proposal, VoteOutcome outcome) implements Outcome {
    }

    static final int TABLE_EVERY = 3;

    private final Aggregates aggregates;
    private final IdGenerator ids;
    private final Legislator legislator;

    public ParliamentSession(Aggregates aggregates, IdGenerator ids, Legislator legislator) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.legislator = Objects.requireNonNull(legislator, "legislator");
    }

    public List<Outcome> execute(Day day) {
        Objects.requireNonNull(day, "day");
        List<Outcome> outcomes = new ArrayList<>();
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            if (!polity.parliament().seated() || !isSessionDay(polity, day)) {
                continue;
            }
            outcomes.add(new Session(polity.id(), polity.proposals().size()));
            List<PersonRef> villagers = villagerMembers(polity.parliament());
            if (villagers.isEmpty()) {
                continue;
            }
            int before = outcomes.size();
            table(polity, villagers, day, outcomes);
            for (Proposal proposal : polity.proposals()) {
                vote(polity, proposal, villagers, day, outcomes);
            }
            if (outcomes.size() > before) {
                aggregates.commit(polity);
            }
        }
        return outcomes;
    }

    private void table(Polity polity, List<PersonRef> villagers, Day day, List<Outcome> outcomes) {
        long roll = mix(polity.id().value().getMostSignificantBits() ^ day.value() * 0x9E3779B97F4A7C15L);
        if (Math.floorMod(roll, TABLE_EVERY) != 0) {
            return;
        }
        if (polity.proposals().stream().anyMatch(open -> open.proposer() instanceof PersonRef)) {
            return;
        }
        PersonRef member = villagers.get(Math.floorMod(roll >>> 8, villagers.size()));
        Optional<Motion> motion = legislator.propose(member, polity);
        if (motion.isEmpty()) {
            return;
        }
        ProposalId id = ProposalId.generate(ids);
        try {
            polity.openProposal(id, motion.get(), member, day);
        } catch (ProposalAlreadyOpenException alreadyOpen) {
            return;
        }
        polity.vote(id, member, true, day);
        outcomes.add(new Tabled(polity.id(), member, motion.get()));
    }

    private void vote(Polity polity, Proposal proposal, List<PersonRef> villagers, Day day, List<Outcome> outcomes) {
        int inFavour = 0;
        int against = 0;
        for (PersonRef member : villagers) {
            if (proposal.votes().stream().anyMatch(vote -> vote.voter().equals(member))) {
                continue;
            }
            Optional<Boolean> decision = legislator.decide(member, proposal, polity);
            if (decision.isEmpty()) {
                continue;
            }
            polity.vote(proposal.id(), member, decision.get(), day);
            if (decision.get()) {
                inFavour++;
            } else {
                against++;
            }
        }
        Proposal current = ProposeLawChange.proposalOf(polity, proposal.id());
        if (inFavour + against > 0) {
            outcomes.add(new Voted(polity.id(), current, inFavour, against));
        }
        VoteOutcome outcome = Ballots.settle(polity, current, Ballots.evaluate(polity, current), ids, day);
        if (outcome != VoteOutcome.OPEN) {
            outcomes.add(new Decided(polity.id(), current, outcome));
        }
    }

    /** Ob das Parlament dieser Polity heute tagt. */
    public static boolean isSessionDay(PolityView polity, Day day) {
        long every = polity.laws().valueOf(StandardParameters.SESSION_DAYS);
        return Math.floorMod(day.value(), every) == 0;
    }

    private static List<PersonRef> villagerMembers(Parliament parliament) {
        return parliament.seats().stream()
                .map(Parliament.Seat::member)
                .filter(PersonRef.class::isInstance)
                .map(PersonRef.class::cast)
                .toList();
    }

    private static long mix(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
