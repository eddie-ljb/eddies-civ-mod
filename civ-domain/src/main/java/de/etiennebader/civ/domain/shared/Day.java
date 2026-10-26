package de.etiennebader.civ.domain.shared;

/**
 * Ein Simulationstag. Tage werden immer von außen übergeben und nie im Kern ermittelt.
 */
public record Day(long value) implements Comparable<Day> {

    public static final Day ZERO = new Day(0);

    public Day {
        if (value < 0) {
            throw new IllegalArgumentException("Ein Tag darf nicht negativ sein: " + value);
        }
    }

    public static Day of(long value) {
        return new Day(value);
    }

    public Day next() {
        return plus(1);
    }

    public Day plus(long days) {
        if (days < 0) {
            throw new IllegalArgumentException("Anzahl Tage darf nicht negativ sein: " + days);
        }
        return new Day(Math.addExact(value, days));
    }

    public boolean isAfter(Day other) {
        return compareTo(other) > 0;
    }

    public boolean isBefore(Day other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Day other) {
        return Long.compare(value, other.value);
    }
}
