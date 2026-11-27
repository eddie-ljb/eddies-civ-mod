package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regierungsbildung: Die stärkste Partei wird beauftragt, eine Mehrheit zu bilden – nur mit Partnern, die politisch
 * zu allen Beteiligten passen (keine Links-Rechts-Bündnisse). Scheitert sie, kommt die nächste an die Reihe; ist
 * keine Mehrheit möglich, gibt es eine Minderheitsregierung der stärksten Partei oder Neuwahlen.
 */
public final class CoalitionBuilder {

    /**
     * Eine Partei im Parlament.
     *
     * @param party   die Partei
     * @param seats   ihre Sitze
     * @param order   Richtung Freiheit/Ordnung (-100 bis 100)
     * @param economy Richtung Steuern/Ausbau (-100 bis 100)
     */
    public record Party(PolityId party, int seats, int order, int economy) {
        int distance(Party other) {
            return Math.max(Math.abs(order - other.order), Math.abs(economy - other.economy));
        }
    }

    public enum Outcome { COALITION, MINORITY, NEW_ELECTION }

    /** Ergebnis: Ausgang, Koalition (außer bei Neuwahl) und die gescheiterten Auftragnehmer in Reihenfolge. */
    public record Result(Outcome outcome, Optional<Coalition> coalition, List<PolityId> failed) {
    }

    private CoalitionBuilder() {
    }

    /**
     * @param parties       alle Parteien mit Sitzen
     * @param totalSeats    Sitze im Parlament insgesamt (auch Parteilose)
     * @param tolerance     größter erlaubter Abstand zweier Koalitionspartner (0 bis 200)
     * @param allowMinority ob eine Minderheitsregierung erlaubt ist
     */
    public static Result form(List<Party> parties, int totalSeats, int tolerance, boolean allowMinority, Day day) {
        List<Party> ranked = parties.stream().filter(p -> p.seats() > 0)
                .sorted(Comparator.comparingInt(Party::seats).reversed()
                        .thenComparing(p -> p.party().value().toString())).toList();
        List<PolityId> failed = new ArrayList<>();
        for (Party formateur : ranked) {
            List<Party> members = new ArrayList<>(List.of(formateur));
            int seats = formateur.seats();
            List<Party> candidates = ranked.stream().filter(p -> !p.equals(formateur))
                    .sorted(Comparator.comparingInt((Party p) -> p.distance(formateur))
                            .thenComparing(Comparator.comparingInt(Party::seats).reversed())).toList();
            for (Party candidate : candidates) {
                if (seats * 2 > totalSeats) {
                    break;
                }
                if (members.stream().allMatch(m -> m.distance(candidate) <= tolerance)) {
                    members.add(candidate);
                    seats += candidate.seats();
                }
            }
            if (seats * 2 > totalSeats) {
                return new Result(Outcome.COALITION, Optional.of(new Coalition(
                        members.stream().map(Party::party).toList(), false, day, List.of())), failed);
            }
            failed.add(formateur.party());
        }
        if (allowMinority && !ranked.isEmpty()) {
            return new Result(Outcome.MINORITY, Optional.of(new Coalition(List.of(ranked.get(0).party()), true, day,
                    List.of())), failed);
        }
        return new Result(Outcome.NEW_ELECTION, Optional.empty(), failed);
    }

    /**
     * Verteilt die Ministerien (in Reihenfolge) nach Sitzstärke auf die Koalitionsparteien (d'Hondt); das erste
     * Ministerium geht an die führende Partei.
     */
    public static Map<String, PolityId> allocate(List<String> ministries, List<PolityId> coalition,
                                                 Map<PolityId, Integer> seats) {
        Map<PolityId, Integer> given = new LinkedHashMap<>();
        coalition.forEach(p -> given.put(p, 0));
        Map<String, PolityId> result = new LinkedHashMap<>();
        for (String ministry : ministries) {
            PolityId best = null;
            double bestScore = -1;
            for (PolityId party : coalition) {
                double score = (double) seats.getOrDefault(party, 1) / (given.get(party) + 1);
                if (given.get(party) == 0 && result.isEmpty() && party.equals(coalition.get(0))) {
                    score = Double.MAX_VALUE;
                }
                if (score > bestScore) {
                    best = party;
                    bestScore = score;
                }
            }
            given.merge(best, 1, Integer::sum);
            result.put(ministry, best);
        }
        return result;
    }
}
