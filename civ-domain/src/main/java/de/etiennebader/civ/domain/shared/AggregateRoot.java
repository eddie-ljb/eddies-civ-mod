package de.etiennebader.civ.domain.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Basisklasse aller Aggregate. Sammelt die Events, die bei Zustandsänderungen entstehen,
 * bis der Aufrufer sie mit {@link #pullEvents()} abholt.
 *
 * @param <E> die Event-Hierarchie des Aggregats
 */
public abstract class AggregateRoot<E extends DomainEvent> {

    private final List<E> pendingEvents = new ArrayList<>();

    protected final void raise(E event) {
        pendingEvents.add(Objects.requireNonNull(event, "event"));
    }

    /**
     * Gibt alle seit dem letzten Aufruf entstandenen Events in Entstehungsreihenfolge zurück
     * und leert die interne Liste.
     */
    public final List<E> pullEvents() {
        List<E> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}
