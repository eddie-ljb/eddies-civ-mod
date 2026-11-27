package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Volkswahl des Ministers eines Ministeriums mit {@link Selection#ELECTION}: Kandidaten, Stimmen, Schluss.
 * Gewählt ist, wer die meisten Stimmen hat; bei Gleichstand der früher Angetretene.
 */
public record MinistryElection(String ministry, Day closesOn, List<ActorRef> candidates,
                               Map<ActorRef, ActorRef> ballots) {

    public MinistryElection {
        candidates = List.copyOf(candidates);
        ballots = Map.copyOf(ballots);
    }

    public static MinistryElection call(String ministry, Day closesOn) {
        return new MinistryElection(ministry, closesOn, List.of(), Map.of());
    }

    public MinistryElection stand(ActorRef candidate) {
        if (candidates.contains(candidate)) {
            throw new GovernmentException(ErrorCode.ALREADY_CANDIDATE, ministry);
        }
        List<ActorRef> next = new ArrayList<>(candidates);
        next.add(candidate);
        return new MinistryElection(ministry, closesOn, next, ballots);
    }

    public MinistryElection cast(ActorRef voter, ActorRef candidate) {
        if (!candidates.contains(candidate)) {
            throw new GovernmentException(ErrorCode.NOT_A_CANDIDATE, ministry);
        }
        Map<ActorRef, ActorRef> next = new LinkedHashMap<>(ballots);
        next.put(voter, candidate);
        return new MinistryElection(ministry, closesOn, candidates, next);
    }

    public long votesFor(ActorRef candidate) {
        return ballots.values().stream().filter(candidate::equals).count();
    }

    public Optional<ActorRef> winner() {
        ActorRef best = null;
        long most = -1;
        for (ActorRef candidate : candidates) {
            long votes = votesFor(candidate);
            if (votes > most) {
                best = candidate;
                most = votes;
            }
        }
        return Optional.ofNullable(best);
    }
}
