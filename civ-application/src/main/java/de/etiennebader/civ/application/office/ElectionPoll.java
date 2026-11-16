package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.SeatAllocation;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Umfrage zu einer laufenden Wahl: Alle Dorfbewohner der Polity werden gefragt, wen sie heute wählen würden, auch ohne
 * Wahlrecht. Die Stimmen der Spieler bleiben geheim und fließen nicht ein.
 *
 * <p>Bei Parlamentswahlen ergibt sich daraus eine Prognose der Sitzverteilung nach Sainte-Laguë.
 *
 * @param candidates Antworten je Kandidat in Reihenfolge der Kandidatur
 * @param parties    Antworten je Liste: die Partei, bei Parteilosen leer und der Kandidat unter {@code independents}
 * @param undecided  wer niemanden wählen würde
 * @param seats      prognostizierte Sitze je Partei bzw. Parteilosem; leer bei Einzelämtern
 */
public record ElectionPoll(Map<ActorRef, Integer> candidates, Map<Optional<PolityId>, Integer> parties,
                           int undecided, Map<Object, Integer> seats) {

    public ElectionPoll {
        candidates = Collections.unmodifiableMap(new LinkedHashMap<>(candidates));
        parties = Collections.unmodifiableMap(new LinkedHashMap<>(parties));
        seats = Collections.unmodifiableMap(new LinkedHashMap<>(seats));
    }

    /** Wie viele Dorfbewohner gefragt wurden. */
    public int sample() {
        return candidates.values().stream().mapToInt(Integer::intValue).sum() + undecided;
    }

    /** Anteil eines Kandidaten an allen Befragten mit Meinung in Prozent. */
    public long percentOf(ActorRef candidate) {
        int decided = sample() - undecided;
        return decided == 0 ? 0 : Math.round(candidates.getOrDefault(candidate, 0) * 100.0 / decided);
    }

    public static ElectionPoll of(PolityView polity, Election election, VillagerVoter voter,
                                  Function<ActorRef, Optional<PolityView>> partyOf) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(partyOf, "partyOf");
        Map<ActorRef, Integer> candidates = new LinkedHashMap<>();
        election.candidates().forEach(candidate -> candidates.put(candidate, 0));
        int undecided = 0;
        for (Membership membership : polity.members()) {
            if (membership.type() != MembershipType.RESIDENT || !(membership.member() instanceof PersonRef person)) {
                continue;
            }
            Optional<ActorRef> choice = election.candidates().isEmpty() ? Optional.empty()
                    : voter.choose(person, election, polity).filter(election::isCandidate);
            if (choice.isPresent()) {
                candidates.merge(choice.get(), 1, Integer::sum);
            } else {
                undecided++;
            }
        }
        Map<Optional<PolityId>, Integer> parties = new LinkedHashMap<>();
        Map<Object, Long> lists = new LinkedHashMap<>();
        Map<Object, Integer> capacity = new LinkedHashMap<>();
        for (Map.Entry<ActorRef, Integer> entry : candidates.entrySet()) {
            Optional<PolityId> party = partyOf.apply(entry.getKey()).map(PolityView::id);
            parties.merge(party, entry.getValue(), Integer::sum);
            Object key = party.<Object>map(id -> id).orElse(entry.getKey());
            lists.merge(key, (long) entry.getValue(), Long::sum);
            capacity.merge(key, 1, Integer::sum);
        }
        Map<Object, Integer> seats = Map.of();
        if (election.office() == Office.PARLIAMENT) {
            int count = (int) Math.min(polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS),
                    election.candidates().size());
            seats = SeatAllocation.sainteLague(lists, capacity, count);
        }
        return new ElectionPoll(candidates, parties, undecided, seats);
    }
}
