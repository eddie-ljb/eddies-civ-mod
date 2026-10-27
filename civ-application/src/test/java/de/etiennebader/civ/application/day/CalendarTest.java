package de.etiennebader.civ.application.day;

import de.etiennebader.civ.domain.shared.Day;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarTest {

    @Test
    @DisplayName("Ein Tag endet genau nach der eingestellten Anzahl Ticks")
    void dayEndsAfterConfiguredTicks() {
        Calendar calendar = new Calendar(3, CalendarState.START);

        assertTrue(calendar.tick().isEmpty());
        assertTrue(calendar.tick().isEmpty());
        assertEquals(Optional.of(Day.of(1)), calendar.tick());

        assertEquals(Day.of(2), calendar.today());
        assertEquals(3, calendar.ticksUntilEndOfDay());
    }

    @Test
    @DisplayName("Ein Tag lässt sich vorzeitig abschließen, z. B. in der Simulation")
    void completesDayImmediately() {
        Calendar calendar = new Calendar(10, CalendarState.START);
        calendar.tick();

        assertEquals(Day.of(1), calendar.completeDay());

        assertEquals(new CalendarState(Day.of(2), 0), calendar.state());
    }

    @Test
    @DisplayName("Ein gespeicherter Stand wird übernommen; zu viele Ticks werden auf den letzten Tick gekappt")
    void restoresAndClampsState() {
        Calendar calendar = new Calendar(10, CalendarState.START);

        calendar.restore(new CalendarState(Day.of(7), 25));

        assertEquals(new CalendarState(Day.of(7), 9), calendar.state());
        assertEquals(Optional.of(Day.of(7)), calendar.tick());
    }

    @Test
    @DisplayName("Ungültige Tageslänge und negative Ticks werden abgelehnt")
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new Calendar(0, CalendarState.START));
        assertThrows(IllegalArgumentException.class, () -> new CalendarState(Day.of(1), -1));
    }
}
