package de.etiennebader.civ.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateRootTest {

    private record Ticked(Day occurredOn) implements DomainEvent {
    }

    private static final class Clock extends AggregateRoot<Ticked> {
        void tick(Day day) {
            raise(new Ticked(day));
        }
    }

    @Test
    @DisplayName("pullEvents() liefert Events in Reihenfolge und leert die Liste")
    void pullEventsReturnsAndClears() {
        Clock clock = new Clock();
        clock.tick(Day.of(1));
        clock.tick(Day.of(2));

        assertEquals(List.of(new Ticked(Day.of(1)), new Ticked(Day.of(2))), clock.pullEvents());
        assertTrue(clock.pullEvents().isEmpty());
    }
}
