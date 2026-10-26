package de.etiennebader.civ.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayTest {

    @Test
    @DisplayName("Negativer Tag ist ungültig")
    void rejectsNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> Day.of(-1));
    }

    @Test
    @DisplayName("next() liefert den Folgetag")
    void nextReturnsFollowingDay() {
        assertEquals(Day.of(6), Day.of(5).next());
    }

    @Test
    @DisplayName("Vergleich von Tagen")
    void comparesDays() {
        Day five = Day.of(5);
        Day six = Day.of(6);

        assertTrue(five.isBefore(six));
        assertTrue(six.isAfter(five));
        assertFalse(five.isAfter(five));
        assertFalse(five.isBefore(five));
    }

    @Test
    @DisplayName("plus() addiert Tage, aber keine negativen")
    void plusAddsDays() {
        assertEquals(Day.of(15), Day.of(5).plus(10));
        assertEquals(Day.ZERO, Day.ZERO.plus(0));
        assertThrows(IllegalArgumentException.class, () -> Day.ZERO.plus(-1));
    }
}
