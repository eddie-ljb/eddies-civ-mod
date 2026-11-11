package de.etiennebader.civ.server.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.shared.ElectionId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine laufende Wahl aus Sicht eines Betrachters. Wer wen gewählt hat, bleibt bis zur Auszählung geheim; sichtbar sind
 * nur die Zahl der abgegebenen Stimmen und die eigene Wahl.
 *
 * @param closesOn   Tag, an dessen Ende ausgezählt wird
 * @param candidates in Reihenfolge der Kandidatur
 * @param ownChoice  wen der Betrachter gewählt hat
 * @param ballots    wie viele Bürger schon abgestimmt haben
 * @param electorate wie viele Bürger wählen dürfen
 */
public record ElectionView(ElectionId id, long closesOn, List<Candidate> candidates, Optional<ActorRef> ownChoice,
                           boolean ownCandidacy, long ballots, long electorate) {

    /** Ein Kandidat mit seinem Wahlprogramm. */
    public record Candidate(ActorRef actor, String name, Platform platform) {

        public Candidate {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(platform, "platform");
        }
    }

    public ElectionView {
        Objects.requireNonNull(id, "id");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(ownChoice, "ownChoice");
    }
}
