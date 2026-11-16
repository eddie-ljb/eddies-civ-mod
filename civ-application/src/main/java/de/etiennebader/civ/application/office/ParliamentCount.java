package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.SeatAllocation;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Auszählung einer Parlamentswahl als personalisierte Verhältniswahl.
 *
 * <p>Jede Stimme gilt einem Kandidaten (gewichtet wie bei {@link ElectionCount}) und zugleich seiner Partei. Kandidaten
 * derselben Partei bilden eine Liste, Parteilose je eine eigene. Die Sitze ({@link StandardParameters#PARLIAMENT_SEATS})
 * werden nach Sainte-Laguë auf die Listen verteilt; innerhalb einer Liste ziehen die Kandidaten mit den meisten
 * persönlichen Stimmen ein, bei Gleichstand wer zuerst kandidiert hat. Das Präsidium bekommt der stärkste Kandidat der
 * stärksten Liste. Wer keine Stimme hat, bekommt keinen Sitz.
 *
 * @param count  Stimmen je Kandidat
 * @param lists  Ergebnis je Liste, die stärkste zuerst
 * @param seats    Sitze in Reihenfolge: das Präsidium zuerst, dann nach Liste und persönlichen Stimmen
 * @param reserves Nachrücker: nicht gewählte Kandidaten der Parteilisten, nach Liste und persönlichen Stimmen
 */
public record ParliamentCount(ElectionCount count, List<ListResult> lists, List<Parliament.Seat> seats,
                              List<Parliament.Seat> reserves) {

    /**
     * Ergebnis einer Liste.
     *
     * @param party       die Partei; leer bei einem parteilosen Einzelbewerber
     * @param candidates  Kandidaten der Liste in Reihenfolge der Kandidatur
     * @param votes       gewichtete Stimmen der Liste
     * @param seats       gewonnene Sitze
     */
    public record ListResult(Optional<PolityId> party, List<ActorRef> candidates, long votes, int seats) {

        public ListResult {
            Objects.requireNonNull(party, "party");
            candidates = List.copyOf(candidates);
        }
    }

    public ParliamentCount {
        Objects.requireNonNull(count, "count");
        lists = List.copyOf(lists);
        seats = List.copyOf(seats);
        reserves = List.copyOf(reserves);
    }

    /**
     * @param partyOf die Partei eines Kandidaten zur Zeit der Auszählung
     */
    public static ParliamentCount of(PolityView polity, Election election, VillagerVoter villagerVoter,
                                     Function<ActorRef, Optional<PolityView>> partyOf) {
        Objects.requireNonNull(polity, "polity");
        return of(polity, election, villagerVoter, partyOf, actor -> Optional.of(polity.id()));
    }

    /**
     * Wie oben, mit Aufschlüsselung nach Wahlbezirken.
     *
     * @param districtOf der Wahlbezirk eines Wählers
     */
    public static ParliamentCount of(PolityView polity, Election election, VillagerVoter villagerVoter,
                                     Function<ActorRef, Optional<PolityView>> partyOf,
                                     Function<ActorRef, Optional<PolityId>> districtOf) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(partyOf, "partyOf");
        ElectionCount count = ElectionCount.of(polity, election, villagerVoter, districtOf);
        Map<Object, List<ActorRef>> members = new LinkedHashMap<>();
        Map<Object, Optional<PolityId>> parties = new LinkedHashMap<>();
        for (ActorRef candidate : election.candidates()) {
            Optional<PolityId> party = partyOf.apply(candidate).map(PolityView::id);
            Object key = party.<Object>map(id -> id).orElse(candidate);
            members.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
            parties.put(key, party);
        }
        Map<Object, Long> votes = new LinkedHashMap<>();
        Map<Object, Integer> capacity = new LinkedHashMap<>();
        members.forEach((key, candidates) -> {
            votes.put(key, candidates.stream().mapToLong(candidate -> count.totals().getOrDefault(candidate, 0L)).sum());
            capacity.put(key, (int) candidates.stream()
                    .filter(candidate -> count.totals().getOrDefault(candidate, 0L) > 0).count());
        });
        int seatCount = (int) Math.min(polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS),
                election.candidates().size());
        Map<Object, Integer> won = SeatAllocation.sainteLague(votes, capacity, seatCount);

        List<Object> ranking = new ArrayList<>(members.keySet());
        ranking.sort(Comparator.comparingLong((Object key) -> votes.get(key)).reversed());
        List<ListResult> lists = new ArrayList<>();
        List<Parliament.Seat> seats = new ArrayList<>();
        List<Parliament.Seat> reserves = new ArrayList<>();
        for (Object key : ranking) {
            List<ActorRef> candidates = members.get(key);
            lists.add(new ListResult(parties.get(key), candidates, votes.get(key), won.get(key)));
            List<ActorRef> ordered = candidates.stream()
                    .sorted(Comparator.comparingLong((ActorRef candidate) -> count.totals().getOrDefault(candidate, 0L))
                            .reversed())
                    .toList();
            for (int index = 0; index < ordered.size(); index++) {
                ActorRef candidate = ordered.get(index);
                Parliament.Seat seat = new Parliament.Seat(candidate, parties.get(key),
                        count.totals().getOrDefault(candidate, 0L));
                if (index < won.get(key)) {
                    seats.add(seat);
                } else if (parties.get(key).isPresent()) {
                    reserves.add(seat);
                }
            }
        }
        return new ParliamentCount(count, lists, seats, reserves);
    }
}
