package de.etiennebader.civ.domain.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine laufende Wahl um das Klerusamt einer Religion: einfache Mehrheitswahl unter den Kandidaten, offen für
 * Spieler und Dorfbewohner gleichermaßen. Bewusst eigenständig statt über die Wahlmaschinerie einer Polity
 * ({@code Election}), da Religionen keine Polity sind (siehe Klassenkommentar von {@link Religion}).
 *
 * @param candidates wer kandidiert, in der Reihenfolge der Kandidatur
 * @param ballots    abgegebene Stimmen, je Wähler die zuletzt gewählte Person
 * @param closesOn   an dessen Ende ausgezählt wird
 */
public record ClergyElection(List<ActorRef> candidates, Map<ActorRef, ActorRef> ballots, Day closesOn) {

    public static final int MAX_CANDIDATES = 20;

    public ClergyElection {
        candidates = List.copyOf(candidates);
        ballots = Map.copyOf(ballots);
        Objects.requireNonNull(closesOn, "closesOn");
    }

    public static ClergyElection open(Day closesOn) {
        return new ClergyElection(List.of(), Map.of(), closesOn);
    }

    /** @throws IllegalArgumentException wenn er schon kandidiert oder {@link #MAX_CANDIDATES} erreicht sind */
    public ClergyElection withCandidate(ActorRef candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidates.contains(candidate)) {
            throw new IllegalArgumentException("Kandidiert schon: " + candidate);
        }
        if (candidates.size() >= MAX_CANDIDATES) {
            throw new IllegalStateException("Höchstens " + MAX_CANDIDATES + " Kandidaten");
        }
        List<ActorRef> updated = new ArrayList<>(candidates);
        updated.add(candidate);
        return new ClergyElection(updated, ballots, closesOn);
    }

    /** Gibt eine Stimme ab oder ändert sie. */
    public ClergyElection withBallot(ActorRef voter, ActorRef candidate) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(candidate, "candidate");
        Map<ActorRef, ActorRef> updated = new LinkedHashMap<>(ballots);
        updated.put(voter, candidate);
        return new ClergyElection(candidates, updated, closesOn);
    }

    /** Wer die meisten Stimmen hat; leer ohne Kandidaten, ohne Stimmen oder bei Gleichstand. */
    public Optional<ActorRef> winner() {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<ActorRef, Long> tally = new LinkedHashMap<>();
        candidates.forEach(candidate -> tally.put(candidate, 0L));
        ballots.values().forEach(candidate -> tally.merge(candidate, 1L, Long::sum));
        long top = tally.values().stream().mapToLong(Long::longValue).max().orElse(0);
        if (top == 0) {
            return Optional.empty();
        }
        List<ActorRef> leaders = tally.entrySet().stream().filter(entry -> entry.getValue() == top)
                .map(Map.Entry::getKey).toList();
        return leaders.size() == 1 ? Optional.of(leaders.getFirst()) : Optional.empty();
    }
}
