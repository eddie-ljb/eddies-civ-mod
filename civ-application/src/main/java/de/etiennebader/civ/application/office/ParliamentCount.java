package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.VotingMethod;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyList;
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
 * Auszählung einer Parlamentswahl nach dem Wahlrecht der Polity.
 *
 * <p>Jede Stimme gilt einem Kandidaten (gewichtet wie bei {@link ElectionCount}) und zugleich seiner Partei. Kandidaten
 * derselben Partei bilden eine Liste, Parteilose je eine eigene. Die Sitze ({@link StandardParameters#PARLIAMENT_SEATS})
 * verteilt das eingestellte Verfahren: Verhältniswahl (Sainte-Laguë, D'Hondt, Hare-Niemeyer, mit Sperrklausel),
 * Mehrheitswahl, Mehrheitswahl in Wahlkreisen oder personalisierte Verhältniswahl. Bei Personenwahl ziehen innerhalb
 * einer Liste die Kandidaten mit den meisten persönlichen Stimmen ein, bei Listenwahl die Listenplätze der Reihe nach
 * (Reihenfolge der Kandidatur). Das Präsidium bekommt der stärkste Kandidat der
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
        return of(polity, election, ElectionCount.of(polity, election, villagerVoter, districtOf), partyOf);
    }

    /**
     * Verteilt die Sitze nach dem Recht der Polity: Verfahren ({@link StandardParameters#PARLIAMENT_VOTING}),
     * Personen- oder Listenwahl ({@link StandardParameters#BALLOT_TYPE}) und Sperrklausel
     * ({@link StandardParameters#THRESHOLD_PERCENT}).
     *
     * @param count bereits ausgezählte Stimmen, z. B. ein Zwischenstand der Live-Auszählung
     */
    public static ParliamentCount of(PolityView polity, Election election, ElectionCount count,
                                     Function<ActorRef, Optional<PolityView>> partyOf) {
        VotingMethod.Parliament method = VotingMethod.Parliament.of(polity.laws().valueOf(StandardParameters.PARLIAMENT_VOTING));
        boolean listBallot = VotingMethod.Ballot.of(polity.laws().valueOf(StandardParameters.BALLOT_TYPE))
                == VotingMethod.Ballot.LIST;
        long threshold = polity.laws().valueOf(StandardParameters.THRESHOLD_PERCENT);

        Map<Object, List<ActorRef>> members = new LinkedHashMap<>();
        Map<Object, Optional<PolityId>> parties = new LinkedHashMap<>();
        Map<ActorRef, Object> listOf = new LinkedHashMap<>();
        for (ActorRef candidate : election.candidates()) {
            Optional<PolityId> party = partyOf.apply(candidate).map(PolityView::id);
            Object key = party.<Object>map(id -> id).orElse(candidate);
            members.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
            parties.put(key, party);
            listOf.put(candidate, key);
        }
        Function<ActorRef, Long> personal = candidate -> count.totals().getOrDefault(candidate, 0L);
        Map<Object, Long> votes = new LinkedHashMap<>();
        Map<Object, List<ActorRef>> ordered = new LinkedHashMap<>();
        Map<Object, Integer> capacity = new LinkedHashMap<>();
        members.forEach((key, candidates) -> {
            votes.put(key, candidates.stream().mapToLong(personal::apply).sum());
            List<ActorRef> order;
            if (listBallot) {
                String partyList = partyOf.apply(candidates.getFirst())
                        .map(party -> party.laws().valueOf(StandardParameters.PARTY_LIST)).orElse("");
                order = PartyList.apply(partyList, candidates);
            } else {
                order = candidates.stream()
                        .sorted(Comparator.comparingLong((ActorRef candidate) -> personal.apply(candidate)).reversed())
                        .toList();
            }
            ordered.put(key, order);
            capacity.put(key, listBallot ? (votes.get(key) > 0 ? candidates.size() : 0)
                    : (int) candidates.stream().filter(candidate -> personal.apply(candidate) > 0).count());
        });
        int seatCount = (int) Math.min(polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS),
                election.candidates().size());

        List<ActorRef> elected = switch (method) {
            case MAJORITY -> majority(ordered, votes, capacity, personal, listBallot, seatCount);
            case DISTRICTS -> districts(count, ordered, listOf, capacity, personal, listBallot, seatCount);
            case MIXED -> mixed(count, ordered, votes, capacity, listOf, personal, listBallot, threshold, seatCount);
            default -> fill(ordered, SeatAllocation.allocate(method.id(), votes, capacity, seatCount, threshold));
        };

        List<Object> ranking = new ArrayList<>(members.keySet());
        ranking.sort(Comparator.comparingLong((Object key) -> votes.get(key)).reversed());
        List<ListResult> lists = new ArrayList<>();
        List<Parliament.Seat> seats = new ArrayList<>();
        List<Parliament.Seat> reserves = new ArrayList<>();
        for (Object key : ranking) {
            List<ActorRef> order = ordered.get(key);
            int won = (int) order.stream().filter(elected::contains).count();
            lists.add(new ListResult(parties.get(key), members.get(key), votes.get(key), won));
            for (ActorRef candidate : order) {
                Parliament.Seat seat = new Parliament.Seat(candidate, parties.get(key), personal.apply(candidate));
                if (elected.contains(candidate)) {
                    seats.add(seat);
                } else if (parties.get(key).isPresent()) {
                    reserves.add(seat);
                }
            }
        }
        return new ParliamentCount(count, lists, seats, reserves);
    }

    /** Die ersten Kandidaten jeder Liste nach ihren Sitzen. */
    private static List<ActorRef> fill(Map<Object, List<ActorRef>> ordered, Map<Object, Integer> won) {
        List<ActorRef> elected = new ArrayList<>();
        ordered.forEach((key, order) -> elected.addAll(order.subList(0, Math.min(order.size(), won.getOrDefault(key, 0)))));
        return elected;
    }

    /**
     * Mehrheitswahl ohne Wahlkreise: bei Personenwahl die Kandidaten mit den meisten Stimmen, bei Listenwahl
     * zuerst die stärkste Liste, so weit sie Kandidaten hat, dann die nächste.
     */
    private static List<ActorRef> majority(Map<Object, List<ActorRef>> ordered, Map<Object, Long> votes,
                                           Map<Object, Integer> capacity, Function<ActorRef, Long> personal,
                                           boolean listBallot, int seats) {
        List<ActorRef> elected = new ArrayList<>();
        if (listBallot) {
            List<Object> strongest = new ArrayList<>(ordered.keySet());
            strongest.sort(Comparator.comparingLong((Object key) -> votes.get(key)).reversed());
            for (Object key : strongest) {
                for (ActorRef candidate : ordered.get(key).subList(0, capacity.get(key))) {
                    if (elected.size() < seats) {
                        elected.add(candidate);
                    }
                }
            }
            return elected;
        }
        ordered.values().stream().flatMap(List::stream)
                .filter(candidate -> personal.apply(candidate) > 0)
                .sorted(Comparator.comparingLong((ActorRef candidate) -> personal.apply(candidate)).reversed())
                .limit(seats).forEach(elected::add);
        return elected;
    }

    /**
     * Mehrheitswahl in Wahlkreisen: Die Sitze gehen nach Hare-Niemeyer auf die Wahlkreise (nach ihren Stimmen), dort
     * an die stärksten Kandidaten bzw. bei Listenwahl an die stärkste Liste des Wahlkreises. Freie Sitze gehen an die
     * stärksten noch nicht gewählten Kandidaten insgesamt.
     */
    private static List<ActorRef> districts(ElectionCount count, Map<Object, List<ActorRef>> ordered,
                                            Map<ActorRef, Object> listOf, Map<Object, Integer> capacity,
                                            Function<ActorRef, Long> personal, boolean listBallot, int seats) {
        Map<Optional<PolityId>, Long> districtVotes = new LinkedHashMap<>();
        count.districts().forEach((district, votes) ->
                districtVotes.put(district, votes.values().stream().mapToLong(Long::longValue).sum()));
        Map<Optional<PolityId>, Integer> perDistrict = SeatAllocation.hareNiemeyer(districtVotes, Map.of(), seats);
        List<ActorRef> elected = new ArrayList<>();
        for (Map.Entry<Optional<PolityId>, Map<ActorRef, Long>> district : count.districts().entrySet()) {
            int districtSeats = perDistrict.getOrDefault(district.getKey(), 0);
            List<ActorRef> local = district.getValue().entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<ActorRef, Long>comparingByValue().reversed())
                    .map(Map.Entry::getKey).toList();
            if (listBallot && !local.isEmpty()) {
                Map<Object, Long> lists = new LinkedHashMap<>();
                district.getValue().forEach((candidate, votes) -> lists.merge(listOf.get(candidate), votes, Long::sum));
                Object strongest = lists.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                local = ordered.get(strongest);
            }
            for (ActorRef candidate : local) {
                if (districtSeats <= 0 || elected.size() >= seats) {
                    break;
                }
                if (!elected.contains(candidate)) {
                    elected.add(candidate);
                    districtSeats--;
                }
            }
        }
        ordered.values().stream().flatMap(List::stream)
                .filter(candidate -> !elected.contains(candidate) && personal.apply(candidate) > 0)
                .sorted(Comparator.comparingLong((ActorRef candidate) -> personal.apply(candidate)).reversed())
                .limit(Math.max(0, seats - elected.size())).toList().forEach(elected::add);
        return elected;
    }

    /**
     * Personalisierte Verhältniswahl: Wer seinen Wahlkreis gewinnt, ist gewählt; die übrigen Sitze gleichen nach
     * Sainte-Laguë (mit Sperrklausel) aus. Hat eine Liste mehr Direktsitze als ihr zustehen, behält sie diese
     * (Überhang) und die anderen teilen den Rest.
     */
    private static List<ActorRef> mixed(ElectionCount count, Map<Object, List<ActorRef>> ordered,
                                        Map<Object, Long> votes, Map<Object, Integer> capacity,
                                        Map<ActorRef, Object> listOf, Function<ActorRef, Long> personal,
                                        boolean listBallot, long threshold, int seats) {
        List<ActorRef> direct = new ArrayList<>();
        for (Map<ActorRef, Long> district : count.districts().values()) {
            district.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                    .filter(winner -> !direct.contains(winner))
                    .ifPresent(winner -> {
                        if (direct.size() < seats) {
                            direct.add(winner);
                        }
                    });
        }
        Map<Object, Integer> directPerList = new LinkedHashMap<>();
        direct.forEach(winner -> directPerList.merge(listOf.get(winner), 1, Integer::sum));
        Map<Object, Long> eligible = new LinkedHashMap<>(votes);
        int pool = seats;
        Map<Object, Integer> won = SeatAllocation.allocate("sainte_lague", eligible, capacity, pool, threshold);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<Object, Integer> entry : directPerList.entrySet()) {
                if (eligible.getOrDefault(entry.getKey(), 0L) > 0 && entry.getValue() > won.getOrDefault(entry.getKey(), 0)) {
                    eligible.put(entry.getKey(), 0L);
                    pool -= entry.getValue();
                    changed = true;
                }
            }
            if (changed) {
                won = SeatAllocation.allocate("sainte_lague", eligible, capacity, Math.max(0, pool), threshold);
            }
        }
        List<ActorRef> elected = new ArrayList<>(direct);
        for (Map.Entry<Object, List<ActorRef>> list : ordered.entrySet()) {
            int total = Math.max(won.getOrDefault(list.getKey(), 0), directPerList.getOrDefault(list.getKey(), 0));
            int have = directPerList.getOrDefault(list.getKey(), 0);
            for (ActorRef candidate : list.getValue()) {
                if (have >= total) {
                    break;
                }
                if (!elected.contains(candidate) && (listBallot || personal.apply(candidate) > 0)) {
                    elected.add(candidate);
                    have++;
                }
            }
        }
        return elected;
    }
}
