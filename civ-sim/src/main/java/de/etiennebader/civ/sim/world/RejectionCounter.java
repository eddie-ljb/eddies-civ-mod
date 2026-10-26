package de.etiennebader.civ.sim.world;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Zählt abgelehnte Vorhaben nach Fehlercode, insgesamt und für den laufenden Tag.
 * Ablehnungen sind in der Simulation normal, z. B. ein Claim über dem Gebietslimit.
 */
public final class RejectionCounter implements Consumer<DomainException> {

    private final Map<ErrorCode, Long> total = new EnumMap<>(ErrorCode.class);
    private long today;

    @Override
    public void accept(DomainException rejection) {
        total.merge(rejection.code(), 1L, Long::sum);
        today++;
    }

    /** Ablehnungen seit dem letzten Aufruf; setzt den Tageszähler zurück. */
    public long takeToday() {
        long count = today;
        today = 0;
        return count;
    }

    public Map<ErrorCode, Long> total() {
        return Map.copyOf(total);
    }
}
