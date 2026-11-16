package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Ballot;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Auszählung einer Wahl.
 *
 * <p>Jeder Bürger hat eine Stimme mit Gewicht 100. Dorfbewohner wählen, wenn
 * {@link StandardParameters#VILLAGER_VOTE_PERCENT} größer als 0 ist, mit diesem Gewicht; wen, entscheidet ein
 * {@link VillagerVoter}. Es gewinnt die höchste Summe; bei Gleichstand der bisherige Amtsinhaber, sonst wer zuerst
 * kandidiert hat.
 *
 * @param totals    gewichtete Stimmen je Kandidat in Reihenfolge der Kandidatur (100 = eine Bürgerstimme)
 * @param villagers wie viele Dorfbewohner abgestimmt haben
 * @param districts gewichtete Stimmen je Wahlbezirk (Gemeinde des Wählers) und Kandidat
 */
public record ElectionCount(Map<ActorRef, Long> totals, int villagers,
                            Map<Optional<PolityId>, Map<ActorRef, Long>> districts) {

    /** Ohne Aufschlüsselung nach Bezirken. */
    public ElectionCount(Map<ActorRef, Long> totals, int villagers) {
        this(totals, villagers, Map.of());
    }

    public ElectionCount {
        totals = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(totals, "totals")));
        Map<Optional<PolityId>, Map<ActorRef, Long>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(districts, "districts")
                .forEach((district, votes) -> copy.put(district, Collections.unmodifiableMap(new LinkedHashMap<>(votes))));
        districts = Collections.unmodifiableMap(copy);
    }

    /** Das Ergebnis je Bezirk für das Wahlarchiv. */
    public List<ElectionResult.District> districtResults() {
        return districts.entrySet().stream()
                .map(entry -> new ElectionResult.District(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Volles Gewicht einer Bürgerstimme. */
    public static final long CITIZEN_WEIGHT = 100;

    public static ElectionCount of(PolityView polity, Election election, VillagerVoter villagerVoter) {
        Objects.requireNonNull(polity, "polity");
        return of(polity, election, villagerVoter, actor -> Optional.of(polity.id()));
    }

    /**
     * Wie oben, mit Aufschlüsselung nach Wahlbezirken.
     *
     * @param districtOf der Wahlbezirk eines Wählers, z. B. seine Gemeinde
     */
    public static ElectionCount of(PolityView polity, Election election, VillagerVoter villagerVoter,
                                   Function<ActorRef, Optional<PolityId>> districtOf) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(districtOf, "districtOf");
        Map<Optional<PolityId>, Map<ActorRef, Long>> districts = new LinkedHashMap<>();
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(villagerVoter, "villagerVoter");
        Map<ActorRef, Long> totals = new LinkedHashMap<>();
        election.candidates().forEach(candidate -> totals.put(candidate, 0L));
        for (Ballot ballot : election.ballots()) {
            totals.merge(ballot.candidate(), CITIZEN_WEIGHT, Long::sum);
            districts.computeIfAbsent(districtOf.apply(ballot.voter()), ignored -> new LinkedHashMap<>())
                    .merge(ballot.candidate(), CITIZEN_WEIGHT, Long::sum);
        }
        long villagerWeight = polity.laws().valueOf(StandardParameters.VILLAGER_VOTE_PERCENT);
        int villagers = 0;
        if (villagerWeight > 0 && !election.candidates().isEmpty()) {
            for (Membership membership : polity.members()) {
                if (membership.type() != MembershipType.RESIDENT || !(membership.member() instanceof PersonRef person)) {
                    continue;
                }
                Optional<ActorRef> choice = villagerVoter.choose(person, election, polity)
                        .filter(election::isCandidate);
                if (choice.isPresent()) {
                    totals.merge(choice.get(), villagerWeight, Long::sum);
                    districts.computeIfAbsent(districtOf.apply(person), ignored -> new LinkedHashMap<>())
                            .merge(choice.get(), villagerWeight, Long::sum);
                    villagers++;
                }
            }
        }
        return new ElectionCount(totals, villagers, districts);
    }

    /** Ob der Führende mehr als die Hälfte aller gewerteten Stimmen hat. */
    public boolean hasAbsoluteMajority() {
        long total = totals.values().stream().mapToLong(Long::longValue).sum();
        long best = totals.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return total > 0 && best * 2 > total;
    }

    /** Die {@code count} Kandidaten mit den meisten Stimmen, bei Gleichstand wer zuerst kandidiert hat. */
    public List<ActorRef> leaders(int count) {
        return totals.entrySet().stream()
                .sorted(Map.Entry.<ActorRef, Long>comparingByValue().reversed())
                .limit(count).map(Map.Entry::getKey).toList();
    }

    /** Der Gewinner; leer, wenn niemand kandidiert hat. */
    public Optional<ActorRef> winner(PolityView polity, Election election) {
        if (totals.isEmpty()) {
            return Optional.empty();
        }
        long best = totals.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        List<ActorRef> leaders = totals.entrySet().stream()
                .filter(entry -> entry.getValue() == best)
                .map(Map.Entry::getKey)
                .toList();
        Optional<ActorRef> incumbent = polity.officeTerm(election.office()).map(OfficeTerm::holder)
                .filter(leaders::contains);
        return incumbent.or(() -> Optional.of(leaders.getFirst()));
    }
}
