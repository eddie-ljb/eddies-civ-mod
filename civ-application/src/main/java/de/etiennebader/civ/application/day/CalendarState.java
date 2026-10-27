package de.etiennebader.civ.application.day;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Stand des Kalenders: der laufende Tag und wie viele Ticks davon schon vergangen sind.
 */
public record CalendarState(Day today, long ticksIntoDay) {

    /** Der erste Tag einer neuen Welt. */
    public static final CalendarState START = new CalendarState(Day.of(1), 0);

    public CalendarState {
        Objects.requireNonNull(today, "today");
        if (ticksIntoDay < 0) {
            throw new IllegalArgumentException("ticksIntoDay darf nicht negativ sein: " + ticksIntoDay);
        }
    }
}
