package de.etiennebader.civ.application.port;

import de.etiennebader.civ.domain.shared.DomainEvent;

import java.util.List;

/**
 * Port, über den die Use Cases die Events erfolgreicher Änderungen veröffentlichen,
 * z. B. an ein Event-Log, an Minecraft-Listener oder an Statistiken.
 */
@FunctionalInterface
public interface DomainEventPublisher {

    /** Verwirft alle Events. */
    DomainEventPublisher NONE = events -> {
    };

    /** Veröffentlicht Events in ihrer Entstehungsreihenfolge. */
    void publish(List<? extends DomainEvent> events);

    /** Veröffentlicht an diesen und danach an {@code next}. */
    default DomainEventPublisher andThen(DomainEventPublisher next) {
        return events -> {
            publish(events);
            next.publish(events);
        };
    }
}
