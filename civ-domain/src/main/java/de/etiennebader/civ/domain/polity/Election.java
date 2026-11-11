package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Eine laufende Wahl für ein Amt. Kandidieren und Abstimmen geht bis zum Ende der Wahl.
 *
 * @param closesOn   der Tag, an dessen Ende ausgezählt wird
 * @param candidates in Reihenfolge der Kandidatur; entscheidet bei Gleichstand
 * @param ballots    in Reihenfolge der Abgabe; jeder Wähler höchstens einmal
 * @param platforms  Wahlprogramm je Kandidat; wer fehlt, hat {@link Platform#NONE}
 */
public record Election(ElectionId id, Office office, Day calledOn, Day closesOn, List<ActorRef> candidates,
                       List<Ballot> ballots, Map<ActorRef, Platform> platforms) {

    /** Eine Wahl ohne Wahlprogramme. */
    public Election(ElectionId id, Office office, Day calledOn, Day closesOn, List<ActorRef> candidates,
                    List<Ballot> ballots) {
        this(id, office, calledOn, closesOn, candidates, ballots, Map.of());
    }

    public Election {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(calledOn, "calledOn");
        Objects.requireNonNull(closesOn, "closesOn");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        ballots = List.copyOf(Objects.requireNonNull(ballots, "ballots"));
        Map<ActorRef, Platform> declared = new LinkedHashMap<>();
        Objects.requireNonNull(platforms, "platforms").forEach((candidate, platform) -> {
            if (platform != Platform.NONE) {
                declared.put(candidate, platform);
            }
        });
        platforms = Collections.unmodifiableMap(declared);
        if (closesOn.isBefore(calledOn)) {
            throw new IllegalArgumentException("Wahl endet vor ihrem Beginn: " + id);
        }
        if (candidates.stream().distinct().count() != candidates.size()) {
            throw new IllegalArgumentException("Doppelte Kandidatur in " + id);
        }
        if (ballots.stream().map(Ballot::voter).distinct().count() != ballots.size()) {
            throw new IllegalArgumentException("Doppelte Stimme in " + id);
        }
        if (!candidates.containsAll(platforms.keySet())) {
            throw new IllegalArgumentException("Wahlprogramm ohne Kandidatur in " + id);
        }
        for (Ballot ballot : ballots) {
            if (!candidates.contains(ballot.candidate())) {
                throw new IllegalArgumentException("Stimme für jemanden, der nicht kandidiert: " + ballot.candidate());
            }
        }
    }

    public boolean isCandidate(ActorRef actor) {
        return candidates.contains(actor);
    }

    /** Das Wahlprogramm eines Kandidaten. */
    public Platform platformOf(ActorRef candidate) {
        return platforms.getOrDefault(candidate, Platform.NONE);
    }

    /** Stimmen je Kandidat, ungewichtet. */
    public long votesFor(ActorRef candidate) {
        return ballots.stream().filter(ballot -> ballot.candidate().equals(candidate)).count();
    }

    Election withCandidate(ActorRef candidate, Platform platform) {
        List<ActorRef> updated = new ArrayList<>(candidates);
        updated.add(candidate);
        Map<ActorRef, Platform> programs = new LinkedHashMap<>(platforms);
        programs.put(candidate, platform);
        return new Election(id, office, calledOn, closesOn, updated, ballots, programs);
    }

    /** Die Wahl mit dieser Stimme; eine frühere Stimme desselben Wählers wird ersetzt. */
    Election withBallot(Ballot ballot) {
        List<Ballot> updated = new ArrayList<>(ballots.stream().filter(cast -> !cast.voter().equals(ballot.voter()))
                .toList());
        updated.add(ballot);
        return new Election(id, office, calledOn, closesOn, candidates, updated, platforms);
    }

    /** Ohne diesen Akteur: seine Kandidatur, die Stimmen für ihn und seine eigene Stimme fallen weg. */
    Election without(ActorRef actor) {
        return new Election(id, office, calledOn, closesOn,
                candidates.stream().filter(candidate -> !candidate.equals(actor)).toList(),
                ballots.stream().filter(ballot -> !ballot.voter().equals(actor) && !ballot.candidate().equals(actor))
                        .toList(),
                platforms.entrySet().stream().filter(entry -> !entry.getKey().equals(actor))
                        .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                                Map::putAll));
    }
}
