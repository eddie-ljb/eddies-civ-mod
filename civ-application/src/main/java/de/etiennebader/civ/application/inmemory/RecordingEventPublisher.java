package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.shared.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkt sich alle veröffentlichten Events, z. B. für Tests.
 */
public final class RecordingEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(List<? extends DomainEvent> published) {
        events.addAll(published);
    }

    public List<DomainEvent> events() {
        return List.copyOf(events);
    }

    /** Liefert alle Events eines Typs in Reihenfolge. */
    public <E extends DomainEvent> List<E> eventsOfType(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public void clear() {
        events.clear();
    }
}
