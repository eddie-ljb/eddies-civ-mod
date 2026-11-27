package de.etiennebader.civ.domain.government;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Ein Haushaltsplan: Etat je Ministerium (Schlüssel → Betrag je Haushaltsperiode).
 *
 * @param allocations Etat je Ministerium
 * @param adoptedOn   Tag der Verabschiedung; leer bei einem Entwurf
 */
public record Budget(Map<String, Long> allocations, Optional<Day> adoptedOn) {

    public static final Budget EMPTY = new Budget(Map.of(), Optional.empty());

    public Budget {
        Objects.requireNonNull(adoptedOn, "adoptedOn");
        allocations = Map.copyOf(new LinkedHashMap<>(allocations));
        allocations.values().forEach(amount -> {
            if (amount < 0) {
                throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, amount);
            }
        });
    }

    public long total() {
        return allocations.values().stream().mapToLong(Long::longValue).sum();
    }

    public long allocationOf(String ministry) {
        return allocations.getOrDefault(ministry, 0L);
    }

    public Budget with(String ministry, long amount) {
        Map<String, Long> next = new LinkedHashMap<>(allocations);
        next.put(ministry, amount);
        return new Budget(next, Optional.empty());
    }

    public Budget adopted(Day day) {
        return new Budget(allocations, Optional.of(day));
    }
}
