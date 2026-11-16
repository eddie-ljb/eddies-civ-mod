package de.etiennebader.civ.domain.polity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Verteilt Sitze auf Listen nach Sainte-Laguë (Divisoren 1, 3, 5, …), wie im Bundestag.
 *
 * <p>Eine Liste bekommt höchstens so viele Sitze, wie sie Kandidaten hat; übrige Sitze gehen an die nächsthöchsten
 * Quotienten anderer Listen. Bei gleichem Quotienten gewinnt die Liste mit mehr Stimmen, dann die frühere.
 */
public final class SeatAllocation {

    private SeatAllocation() {
    }

    /**
     * @param votes    Stimmen je Liste in fester Reihenfolge
     * @param capacity Kandidaten je Liste; fehlt eine Liste, ist sie unbegrenzt
     * @param seats    zu vergebende Sitze
     * @param <K>      Schlüssel einer Liste
     * @return Sitze je Liste in der Reihenfolge von {@code votes}, auch Listen ohne Sitz
     */
    public static <K> Map<K, Integer> sainteLague(Map<K, Long> votes, Map<K, Integer> capacity, int seats) {
        Objects.requireNonNull(votes, "votes");
        Objects.requireNonNull(capacity, "capacity");
        if (seats < 0) {
            throw new IllegalArgumentException("Negative Sitzzahl: " + seats);
        }
        Map<K, Integer> result = new LinkedHashMap<>();
        votes.keySet().forEach(list -> result.put(list, 0));
        List<K> order = List.copyOf(votes.keySet());
        for (int round = 0; round < seats; round++) {
            K best = null;
            double bestQuotient = -1;
            for (K list : order) {
                long listVotes = votes.get(list);
                int won = result.get(list);
                if (listVotes <= 0 || won >= capacity.getOrDefault(list, Integer.MAX_VALUE)) {
                    continue;
                }
                double quotient = listVotes / (2.0 * won + 1);
                if (quotient > bestQuotient || quotient == bestQuotient && listVotes > votes.get(best)) {
                    best = list;
                    bestQuotient = quotient;
                }
            }
            if (best == null) {
                break;
            }
            result.merge(best, 1, Integer::sum);
        }
        return result;
    }
}
