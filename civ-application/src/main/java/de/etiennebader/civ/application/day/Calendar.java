package de.etiennebader.civ.application.day;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;

/**
 * Die Zeitrechnung der Zivilisation: zählt Spielticks und meldet, wann ein Tag zu Ende ist.
 *
 * <p>Bewusst unabhängig von der Tageszeit der Minecraft-Welt: Schlafen oder {@code /time set}
 * überspringen keine Steuertage, und jede Dimension erlebt denselben Kalender.
 */
public final class Calendar {

    /** Ein Minecraft-Tag dauert 24000 Ticks, also 20 Minuten. */
    public static final long MINECRAFT_DAY_TICKS = 24_000;

    private final long ticksPerDay;
    private Day today;
    private long ticksIntoDay;

    public Calendar(long ticksPerDay, CalendarState start) {
        if (ticksPerDay < 1) {
            throw new IllegalArgumentException("ticksPerDay muss mindestens 1 sein: " + ticksPerDay);
        }
        Objects.requireNonNull(start, "start");
        this.ticksPerDay = ticksPerDay;
        restore(start);
    }

    /** Ein Kalender mit Minecraft-Tageslänge, der an Tag 1 beginnt. */
    public static Calendar standard() {
        return new Calendar(MINECRAFT_DAY_TICKS, CalendarState.START);
    }

    /**
     * Rückt um einen Tick vor.
     *
     * @return der gerade beendete Tag, falls dieser Tick ihn abgeschlossen hat
     */
    public Optional<Day> tick() {
        ticksIntoDay++;
        if (ticksIntoDay < ticksPerDay) {
            return Optional.empty();
        }
        return Optional.of(completeDay());
    }

    /**
     * Beendet den laufenden Tag sofort, unabhängig von den Ticks; z. B. für die Simulation.
     *
     * @return der beendete Tag
     */
    public Day completeDay() {
        Day completed = today;
        today = today.next();
        ticksIntoDay = 0;
        return completed;
    }

    /** Übernimmt einen gespeicherten Stand. Ticks über die Tageslänge hinaus werden gekappt. */
    public void restore(CalendarState state) {
        Objects.requireNonNull(state, "state");
        this.today = state.today();
        this.ticksIntoDay = Math.min(state.ticksIntoDay(), ticksPerDay - 1);
    }

    public Day today() {
        return today;
    }

    public long ticksPerDay() {
        return ticksPerDay;
    }

    public long ticksUntilEndOfDay() {
        return ticksPerDay - ticksIntoDay;
    }

    public CalendarState state() {
        return new CalendarState(today, ticksIntoDay);
    }
}
