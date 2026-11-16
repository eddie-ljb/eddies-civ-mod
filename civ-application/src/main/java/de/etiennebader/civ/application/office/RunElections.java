package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Tagesende für Ämter: fällige Wahlen auszählen und neue ausrufen.
 *
 * <ul>
 *   <li>Eine Wahl, deren letzter Tag heute ist, wird ausgezählt ({@link ElectionCount}). Der Gewinner bekommt das Amt
 *       für {@link StandardParameters#TERM_DAYS} Tage, bei 0 unbefristet. Hat niemand kandidiert, bleibt alles, wie es
 *       ist; eine abgelaufene Amtszeit verlängert sich um eine Amtszeit, damit nicht täglich neu gewählt wird.</li>
 *   <li>Gilt eine Amtszeit ({@code term_days} &gt; 0), wird für jedes abgelaufene Amt eine Wahl ausgerufen, für das
 *       Bürgermeisteramt auch, wenn es noch nie gewählt wurde.</li>
 *   <li>Parlament ({@link StandardParameters#PARLIAMENT_SEATS} &gt; 0): Eine Parlamentswahl wird nach
 *       {@link ParliamentCount} ausgezählt; ausgerufen wird sie, solange kein Parlament sitzt oder seine Wahlperiode
 *       abgelaufen ist. Sieht das Recht keine Sitze mehr vor, wird das Parlament aufgelöst.</li>
 *   <li>Haben Dorfbewohner Wahlrecht, kandidieren Parteimitglieder unter ihnen bei laufenden Parlamentswahlen für ihre
 *       Partei, je Partei höchstens so viele, wie es Sitze gibt.</li>
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

    /** Ein Parlament wurde gewählt. */
    public record Seated(PolityId polity, Office office, ParliamentCount count) implements Outcome {
    }

    /** Das Parlament wurde aufgelöst, weil das Recht keine Sitze mehr vorsieht. */
    public record Dissolved(PolityId polity, Office office) implements Outcome {
    }

    /** Dorfbewohner kandidieren für ihre Parteien. */
    public record Nominated(PolityId polity, Office office, List<ActorRef> candidates) implements Outcome {

        public Nominated {
            candidates = List.copyOf(candidates);
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;
    private final VillagerVoter villagerVoter;
    private final Function<ActorRef, Optional<PolityView>> partyOf;
    private final Function<ActorRef, Optional<PolityId>> homeOf;

    /** Ohne Parteien, z. B. für Tests. */
    public RunElections(Aggregates aggregates, IdGenerator ids, VillagerVoter villagerVoter) {
        this(aggregates, ids, villagerVoter, actor -> Optional.empty());
    }

    /** Ohne Wohnort für Wahlbezirke in Bünden. */
    public RunElections(Aggregates aggregates, IdGenerator ids, VillagerVoter villagerVoter,
                        Function<ActorRef, Optional<PolityView>> partyOf) {
        this(aggregates, ids, villagerVoter, partyOf, actor -> Optional.empty());
    }

    /**
     * @param partyOf die Partei eines Akteurs
     */
    /**
     * @param partyOf die Partei eines Akteurs
     * @param homeOf  die Gemeinde eines Akteurs: sein Wahlbezirk bei Wahlen eines Bundes
     */
    public RunElections(Aggregates aggregates, IdGenerator ids, VillagerVoter villagerVoter,
                        Function<ActorRef, Optional<PolityView>> partyOf,
                        Function<ActorRef, Optional<PolityId>> homeOf) {
        this.homeOf = Objects.requireNonNull(homeOf, "homeOf");
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.villagerVoter = Objects.requireNonNull(villagerVoter, "villagerVoter");
        this.partyOf = Objects.requireNonNull(partyOf, "partyOf");
    }

    public List<Outcome> execute(Day day) {
        Objects.requireNonNull(day, "day");
        List<Outcome> outcomes = new ArrayList<>();
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            int before = outcomes.size();
            Set<Office> justClosed = closeDue(polity, day, outcomes);
            callDue(polity, day, justClosed, outcomes);
            callParliament(polity, day, justClosed, outcomes);
            nominateVillagers(polity, day, outcomes);
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
            if (election.office() == Office.PARLIAMENT) {
                closeParliament(polity, election, until, day, outcomes);
                continue;
            }
            ElectionCount count = ElectionCount.of(polity, election, villagerVoter, districtOf(polity));
            Optional<ActorRef> winner = count.winner(polity, election);
            polity.closeElection(election.id(), winner, until, day);
            polity.recordResult(new ElectionResult(election.id(), election.office(), day, count.districtResults(),
                    winner.stream().toList()));
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
            if (!office.single() || polity.electionFor(office).isPresent() || justClosed.contains(office)) {
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

    private void closeParliament(Polity polity, Election election, Optional<Day> until, Day day,
                                 List<Outcome> outcomes) {
        ParliamentCount count = ParliamentCount.of(polity, election, villagerVoter, partyOf, districtOf(polity));
        polity.seatParliament(election.id(), count.seats(), count.reserves(), until, day);
        polity.recordResult(new ElectionResult(election.id(), Office.PARLIAMENT, day, count.count().districtResults(),
                count.seats().stream().map(Parliament.Seat::member).toList()));
        if (!count.seats().isEmpty()) {
            outcomes.add(new Seated(polity.id(), Office.PARLIAMENT, count));
            return;
        }
        outcomes.add(new NoCandidates(polity.id(), Office.PARLIAMENT));
        if (polity.parliament().expiredOn(day)) {
            polity.extendParliament(until, day);
        }
    }

    /** Wahlbezirk eines Wählers: in einer Gemeinde sie selbst, in einem Bund seine Gemeinde. */
    private Function<ActorRef, Optional<PolityId>> districtOf(Polity polity) {
        if (polity.kind() == PolityKind.FEDERATION) {
            return homeOf;
        }
        return actor -> Optional.of(polity.id());
    }

    private void callParliament(Polity polity, Day day, Set<Office> justClosed, List<Outcome> outcomes) {
        long seats = polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS);
        if (seats <= 0) {
            if (polity.dissolveParliament(day)) {
                outcomes.add(new Dissolved(polity.id(), Office.PARLIAMENT));
            }
            return;
        }
        if (polity.electionFor(Office.PARLIAMENT).isPresent() || justClosed.contains(Office.PARLIAMENT)) {
            return;
        }
        Parliament parliament = polity.parliament();
        if (!parliament.seated() || parliament.expiredOn(day)) {
            CallElection.call(polity, Office.PARLIAMENT, ids, day);
            outcomes.add(new Called(polity.id(), Office.PARLIAMENT,
                    polity.electionFor(Office.PARLIAMENT).orElseThrow().closesOn()));
        }
    }

    /** Parteimitglieder unter den Dorfbewohnern kandidieren bei der laufenden Parlamentswahl. */
    private void nominateVillagers(Polity polity, Day day, List<Outcome> outcomes) {
        Optional<Election> open = polity.electionFor(Office.PARLIAMENT);
        long seats = polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS);
        if (open.isEmpty() || seats <= 0 || polity.laws().valueOf(StandardParameters.VILLAGER_VOTE_PERCENT) <= 0) {
            return;
        }
        Map<PolityId, Long> standing = new HashMap<>();
        for (ActorRef candidate : open.get().candidates()) {
            partyOf.apply(candidate).ifPresent(party -> standing.merge(party.id(), 1L, Long::sum));
        }
        List<ActorRef> nominated = new ArrayList<>();
        for (Membership membership : polity.members()) {
            if (!(membership.member() instanceof PersonRef villager) || open.get().isCandidate(villager)) {
                continue;
            }
            Optional<PolityView> party = partyOf.apply(villager);
            if (party.isEmpty() || standing.getOrDefault(party.get().id(), 0L) >= seats) {
                continue;
            }
            Platform platform = party.get().partyProfile().map(PartyProfile::platform).orElse(Platform.NONE);
            polity.standForElection(open.get().id(), villager, platform, day);
            standing.merge(party.get().id(), 1L, Long::sum);
            nominated.add(villager);
        }
        if (!nominated.isEmpty()) {
            outcomes.add(new Nominated(polity.id(), Office.PARLIAMENT, nominated));
        }
    }
}
