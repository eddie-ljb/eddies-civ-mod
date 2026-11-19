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
 * Parlamentsbetrieb über mehrere Tage: Dorfbewohner mit Sitz können an jedem Tag einen Antrag stellen, nicht nur am
 * Sitzungstag. Jeder Antrag muss mindestens {@link StandardParameters#PARLIAMENT_MOTION_MIN_OPEN_DAYS} offen sein,
 * bevor am Sitzungstag ({@link StandardParameters#SESSION_DAYS}) darüber abgestimmt wird; die endgültige
 * Tagesordnung wird dafür immer schon einen Tag vorher bekanntgegeben. Danach zählt {@link TallyProposals} am
 * Fristende ({@link StandardParameters#VOTE_DAYS}) noch unentschiedene Anträge aus.
 *
 * <ul>
 *   <li>An jedem dritten Tag (fest je Parlament und Tag gewürfelt) stellt ein Dorfbewohner-Abgeordneter einen Antrag,
 *       wenn ihm etwas wichtig genug ist und kein anderer Antrag eines Dorfbewohners offen ist. Das kann an jedem
 *       Tag passieren, nicht nur an einem Sitzungstag.</li>
 *   <li>Am Tag vor einer Sitzung wird die Tagesordnung bekanntgegeben: alle Anträge, die am Sitzungstag die
 *       Mindestoffenzeit erreicht haben werden.</li>
 *   <li>Am Sitzungstag stimmt zu jedem Antrag auf der Tagesordnung jeder Dorfbewohner-Abgeordnete ab, der noch nicht
 *       abgestimmt hat (oder enthält sich).</li>
 *   <li>Steht danach die Mehrheit fest, wird entschieden — außer das Gesetz
 *       {@link StandardParameters#FULL_TURNOUT_REQUIRED} verlangt vorher die Stimme aller Abgeordneten.</li>
 * </ul>
 * Spieler mit Sitz stimmen selbst ab, jederzeit; was sie bis zur Frist nicht tun, zählt als Enthaltung.
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

    /** Die Tagesordnung für die nächste Sitzung (morgen) steht fest. */
    public record AgendaAnnounced(PolityId polity, int agenda) implements Outcome {
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
            if (!polity.parliament().seated()) {
                continue;
            }
            List<PersonRef> villagers = villagerMembers(polity.parliament());
            int before = outcomes.size();
            if (!villagers.isEmpty()) {
                table(polity, villagers, day, outcomes);
            }
            if (isSessionDay(polity, day)) {
                List<Proposal> agenda = onAgenda(polity, day);
                outcomes.add(new Session(polity.id(), agenda.size()));
                if (!villagers.isEmpty()) {
                    for (Proposal proposal : agenda) {
                        vote(polity, proposal, villagers, day, outcomes);
                    }
                }
            }
            if (isSessionDay(polity, day.next())) {
                int tomorrow = onAgenda(polity, day.next()).size();
                if (tomorrow > 0) {
                    outcomes.add(new AgendaAnnounced(polity.id(), tomorrow));
                }
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

    /**
     * Anträge, die am gegebenen Tag auf der Tagesordnung stehen: offen seit mindestens
     * {@link StandardParameters#PARLIAMENT_MOTION_MIN_OPEN_DAYS}. Da die Mindestoffenzeit feststeht, sobald ein
     * Antrag gestellt ist, ist das Ergebnis für morgen schon heute bekannt — genau das macht die vorherige
     * Bekanntgabe der Tagesordnung möglich.
     */
    private static List<Proposal> onAgenda(PolityView polity, Day day) {
        long minOpenDays = polity.laws().valueOf(StandardParameters.PARLIAMENT_MOTION_MIN_OPEN_DAYS);
        return polity.proposals().stream()
                .filter(proposal -> !day.isBefore(proposal.openedOn().plus(minOpenDays)))
                .toList();
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
