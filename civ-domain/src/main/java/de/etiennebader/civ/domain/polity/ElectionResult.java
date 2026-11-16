package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Das Ergebnis einer beendeten Wahl, aufgeschlüsselt nach Wahlbezirken.
 *
 * <p>Wahlbezirke sind bei einer Gemeinde die Gemeinde selbst, bei einem Bund seine Gemeinden: Jede Stimme zählt in
 * dem Bezirk, in dem der Wähler wohnt.
 *
 * @param districts Ergebnis je Bezirk in fester Reihenfolge
 * @param winners   wer gewonnen hat: der neue Amtsinhaber bzw. die Abgeordneten; leer ohne Kandidaten
 */
public record ElectionResult(ElectionId id, Office office, Day closedOn, List<District> districts,
                             List<ActorRef> winners) {

    /**
     * Ergebnis eines Wahlbezirks.
     *
     * @param district die Gemeinde; leer für Stimmen, die keinem Bezirk zuzuordnen sind
     * @param votes    gewichtete Stimmen je Kandidat (100 = eine Bürgerstimme), nur Kandidaten mit Stimmen
     */
    public record District(Optional<PolityId> district, Map<ActorRef, Long> votes) {

        public District {
            Objects.requireNonNull(district, "district");
            votes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(votes, "votes")));
        }

        public long total() {
            return votes.values().stream().mapToLong(Long::longValue).sum();
        }

        /** Wer im Bezirk vorn liegt; leer ohne Stimmen. */
        public Optional<ActorRef> leader() {
            return votes.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        }
    }

    public ElectionResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(closedOn, "closedOn");
        districts = List.copyOf(Objects.requireNonNull(districts, "districts"));
        winners = List.copyOf(Objects.requireNonNull(winners, "winners"));
    }

    /** Stimmen je Kandidat über alle Bezirke. */
    public Map<ActorRef, Long> totals() {
        Map<ActorRef, Long> totals = new LinkedHashMap<>();
        for (District district : districts) {
            district.votes().forEach((candidate, votes) -> totals.merge(candidate, votes, Long::sum));
        }
        return totals;
    }
}
