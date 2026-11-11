package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Ballot;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Auszählung einer Wahl.
 *
 * <p>Jeder Bürger hat eine Stimme mit Gewicht 100. Dorfbewohner wählen, wenn
 * {@link StandardParameters#VILLAGER_VOTE_PERCENT} größer als 0 ist, mit diesem Gewicht; wen, entscheidet ein
 * {@link VillagerVoter}. Es gewinnt die höchste Summe; bei Gleichstand der bisherige Amtsinhaber, sonst wer zuerst
 * kandidiert hat.
 *
 * @param totals   gewichtete Stimmen je Kandidat in Reihenfolge der Kandidatur (100 = eine Bürgerstimme)
 * @param villagers wie viele Dorfbewohner abgestimmt haben
 */
public record ElectionCount(Map<ActorRef, Long> totals, int villagers) {

    public ElectionCount {
        totals = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(totals, "totals")));
    }

    /** Volles Gewicht einer Bürgerstimme. */
    public static final long CITIZEN_WEIGHT = 100;

    public static ElectionCount of(PolityView polity, Election election, VillagerVoter villagerVoter) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(villagerVoter, "villagerVoter");
        Map<ActorRef, Long> totals = new LinkedHashMap<>();
        election.candidates().forEach(candidate -> totals.put(candidate, 0L));
        for (Ballot ballot : election.ballots()) {
            totals.merge(ballot.candidate(), CITIZEN_WEIGHT, Long::sum);
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
                    villagers++;
                }
            }
        }
        return new ElectionCount(totals, villagers);
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
