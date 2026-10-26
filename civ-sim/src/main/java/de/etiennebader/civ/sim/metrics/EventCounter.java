package de.etiennebader.civ.sim.metrics;

import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.shared.DomainEvent;

import java.util.List;

/**
 * Zählt veröffentlichte Events, insgesamt und für den laufenden Tag.
 */
public final class EventCounter implements DomainEventPublisher {

    private long total;
    private long today;

    @Override
    public void publish(List<? extends DomainEvent> events) {
        total += events.size();
        today += events.size();
    }

    /** Events seit dem letzten Aufruf; setzt den Tageszähler zurück. */
    public long takeToday() {
        long count = today;
        today = 0;
        return count;
    }

    public long total() {
        return total;
    }
}
