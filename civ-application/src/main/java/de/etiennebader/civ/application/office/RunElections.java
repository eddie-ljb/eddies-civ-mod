package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tagesende für Ämter: fällige Wahlen auszählen und neue ausrufen.
 *
 * <ul>
 *   <li>Eine Wahl, deren letzter Tag heute ist, wird ausgezählt ({@link ElectionCount}). Der Gewinner bekommt das Amt
 *       für {@link StandardParameters#TERM_DAYS} Tage, bei 0 unbefristet. Hat niemand kandidiert, bleibt alles, wie es
 *       ist; eine abgelaufene Amtszeit verlängert sich um eine Amtszeit, damit nicht täglich neu gewählt wird.</li>
 *   <li>Gilt eine Amtszeit ({@code term_days} &gt; 0), wird für jedes abgelaufene Amt eine Wahl ausgerufen, für das
 *       Bürgermeisteramt auch, wenn es noch nie gewählt wurde.</li>
 * </ul>
 */
public final class RunElections {

    /** Was am Tagesende passiert ist. */
    public sealed interface Outcome {

        PolityId polity();

        Office office();
    }

    /** Eine neue Wahl wurde ausgerufen. */
    public record Called(PolityId polity, Office office, Day closesOn) implements Outcome {
    }

    /** Eine Wahl wurde entschieden. */
    public record Decided(PolityId polity, Office office, ActorRef winner, ElectionCount count) implements Outcome {
    }

    /** Niemand hat kandidiert. */
    public record NoCandidates(PolityId polity, Office office) implements Outcome {
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;
    private final VillagerVoter villagerVoter;

    public RunElections(Aggregates aggregates, IdGenerator ids, VillagerVoter villagerVoter) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.villagerVoter = Objects.requireNonNull(villagerVoter, "villagerVoter");
    }

    public List<Outcome> execute(Day day) {
        Objects.requireNonNull(day, "day");
        List<Outcome> outcomes = new ArrayList<>();
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            int before = outcomes.size();
            Set<Office> justClosed = closeDue(polity, day, outcomes);
            callDue(polity, day, justClosed, outcomes);
            if (outcomes.size() > before) {
                aggregates.commit(polity);
            }
        }
        return outcomes;
    }

    /** @return die Ämter, deren Wahl heute ausgezählt wurde; für sie wird heute keine neue Wahl ausgerufen */
    private Set<Office> closeDue(Polity polity, Day day, List<Outcome> outcomes) {
        Set<Office> closed = EnumSet.noneOf(Office.class);
        long termDays = polity.laws().valueOf(StandardParameters.TERM_DAYS);
        Optional<Day> until = termDays > 0 ? Optional.of(day.plus(termDays)) : Optional.empty();
        for (Election election : polity.elections()) {
            if (day.isBefore(election.closesOn())) {
                continue;
            }
            closed.add(election.office());
            ElectionCount count = ElectionCount.of(polity, election, villagerVoter);
            Optional<ActorRef> winner = count.winner(polity, election);
            polity.closeElection(election.id(), winner, until, day);
            if (winner.isPresent()) {
                outcomes.add(new Decided(polity.id(), election.office(), winner.get(), count));
                continue;
            }
            outcomes.add(new NoCandidates(polity.id(), election.office()));
            Optional<OfficeTerm> incumbent = polity.officeTerm(election.office());
            if (incumbent.isPresent() && incumbent.get().expiredOn(day)) {
                polity.fillOffice(election.office(), incumbent.get().holder(), until, day);
            }
        }
        return closed;
    }

    private void callDue(Polity polity, Day day, Set<Office> justClosed, List<Outcome> outcomes) {
        if (polity.laws().valueOf(StandardParameters.TERM_DAYS) <= 0) {
            return;
        }
        for (Office office : Office.values()) {
            if (polity.electionFor(office).isPresent() || justClosed.contains(office)) {
                continue;
            }
            Optional<OfficeTerm> term = polity.officeTerm(office);
            boolean due = term.map(current -> current.expiredOn(day)).orElse(office == Office.MAYOR);
            if (due) {
                CallElection.call(polity, office, ids, day);
                outcomes.add(new Called(polity.id(), office, polity.electionFor(office).orElseThrow().closesOn()));
            }
        }
    }
}
