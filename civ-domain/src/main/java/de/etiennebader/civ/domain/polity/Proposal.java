package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ProposalId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Eine offene Abstimmung über die Änderung einer Regelung.
 *
 * @param votes in Reihenfolge der Abgabe; jeder Wähler höchstens einmal
 */
public record Proposal(ProposalId id, ParameterValue<?> change, ActorRef proposer, Day openedOn, List<Vote> votes) {

    public Proposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(openedOn, "openedOn");
        votes = List.copyOf(Objects.requireNonNull(votes, "votes"));
        if (votes.stream().map(Vote::voter).distinct().count() != votes.size()) {
            throw new IllegalArgumentException("Doppelte Stimme in " + id);
        }
    }

    public long inFavour() {
        return votes.stream().filter(Vote::inFavour).count();
    }

    public long against() {
        return votes.size() - inFavour();
    }

    /** Die Abstimmung mit dieser Stimme; eine frühere Stimme desselben Wählers wird ersetzt. */
    Proposal with(Vote vote) {
        List<Vote> updated = new ArrayList<>(votes.stream().filter(cast -> !cast.voter().equals(vote.voter())).toList());
        updated.add(vote);
        return new Proposal(id, change, proposer, openedOn, updated);
    }
}
