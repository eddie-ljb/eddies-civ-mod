package de.etiennebader.civ.domain.polity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Verteilt Sitze auf Listen: nach Sainte-Laguë (Divisoren 1, 3, 5, …), wie im Bundestag, nach D'Hondt oder
 * Hare-Niemeyer, jeweils mit optionaler Sperrklausel.
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

    /**
     * Verteilt Sitze nach D'Hondt (Divisoren 1, 2, 3, …); sonst wie {@link #sainteLague}.
     */
    public static <K> Map<K, Integer> dhondt(Map<K, Long> votes, Map<K, Integer> capacity, int seats) {
        return divisor(votes, capacity, seats, won -> won + 1.0);
    }

    /**
     * Verteilt Sitze nach Hare-Niemeyer: Jede Liste bekommt den ganzzahligen Teil ihrer Quote, die übrigen Sitze gehen
     * nach den größten Nachkommastellen. Sitze, die eine Liste mangels Kandidaten nicht besetzen kann, gehen nach
     * Sainte-Laguë an die anderen.
     */
    public static <K> Map<K, Integer> hareNiemeyer(Map<K, Long> votes, Map<K, Integer> capacity, int seats) {
        Objects.requireNonNull(votes, "votes");
        Objects.requireNonNull(capacity, "capacity");
        if (seats < 0) {
            throw new IllegalArgumentException("Negative Sitzzahl: " + seats);
        }
        Map<K, Integer> result = new LinkedHashMap<>();
        votes.keySet().forEach(list -> result.put(list, 0));
        long total = votes.values().stream().filter(value -> value > 0).mapToLong(Long::longValue).sum();
        if (total == 0 || seats == 0) {
            return result;
        }
        Map<K, Double> remainders = new LinkedHashMap<>();
        int given = 0;
        for (Map.Entry<K, Long> entry : votes.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            double quota = (double) entry.getValue() * seats / total;
            int whole = Math.min((int) Math.floor(quota), capacity.getOrDefault(entry.getKey(), Integer.MAX_VALUE));
            result.put(entry.getKey(), whole);
            remainders.put(entry.getKey(), quota - Math.floor(quota));
            given += whole;
        }
        List<K> byRemainder = remainders.entrySet().stream()
                .sorted(Map.Entry.<K, Double>comparingByValue().reversed()
                        .thenComparing(entry -> -votes.get(entry.getKey())))
                .map(Map.Entry::getKey).toList();
        for (K list : byRemainder) {
            if (given >= seats) {
                break;
            }
            if (result.get(list) < capacity.getOrDefault(list, Integer.MAX_VALUE)) {
                result.merge(list, 1, Integer::sum);
                given++;
            }
        }
        if (given < seats) {
            Map<K, Integer> rest = new LinkedHashMap<>();
            capacity.forEach((list, max) -> rest.put(list, Math.max(0, max - result.getOrDefault(list, 0))));
            votes.keySet().forEach(list -> rest.putIfAbsent(list, Integer.MAX_VALUE - result.get(list)));
            sainteLague(votes, rest, seats - given).forEach((list, extra) -> result.merge(list, extra, Integer::sum));
        }
        return result;
    }

    /**
     * Verteilt Sitze nach einem Verfahren; Listen unter der Sperrklausel bekommen keine Sitze.
     *
     * @param method           {@code sainte_lague}, {@code dhondt} oder {@code hare_niemeyer}; unbekannte wie
     *                         Sainte-Laguë
     * @param thresholdPercent Mindestanteil an allen Stimmen in Prozent, 0 ohne Sperrklausel
     */
    public static <K> Map<K, Integer> allocate(String method, Map<K, Long> votes, Map<K, Integer> capacity, int seats,
                                               long thresholdPercent) {
        Map<K, Long> eligible = withThreshold(votes, thresholdPercent);
        return switch (method) {
            case "dhondt" -> dhondt(eligible, capacity, seats);
            case "hare_niemeyer" -> hareNiemeyer(eligible, capacity, seats);
            default -> sainteLague(eligible, capacity, seats);
        };
    }

    /** Die Stimmen, wobei Listen unter der Sperrklausel 0 bekommen. Fiele dadurch jede Liste weg, gilt keine Klausel. */
    public static <K> Map<K, Long> withThreshold(Map<K, Long> votes, long thresholdPercent) {
        long total = votes.values().stream().filter(value -> value > 0).mapToLong(Long::longValue).sum();
        if (thresholdPercent <= 0 || total == 0) {
            return votes;
        }
        Map<K, Long> eligible = new LinkedHashMap<>();
        votes.forEach((list, value) -> eligible.put(list, value * 100 >= total * thresholdPercent ? value : 0L));
        return eligible.values().stream().anyMatch(value -> value > 0) ? eligible : votes;
    }

    private static <K> Map<K, Integer> divisor(Map<K, Long> votes, Map<K, Integer> capacity, int seats,
                                               java.util.function.IntToDoubleFunction divisor) {
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
                double quotient = listVotes / divisor.applyAsDouble(won);
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
