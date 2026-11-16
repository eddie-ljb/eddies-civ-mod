package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Kurs eines Tages in Einheiten je Aktie.
 *
 * @param volume gehandelte Stückzahl; 0 an Tagen ohne Handel
 */
public record Quote(Day day, long open, long high, long low, long close, long volume) {

    public Quote {
        Objects.requireNonNull(day, "day");
        if (open <= 0 || high < Math.max(open, close) || low > Math.min(open, close) || low <= 0 || volume < 0) {
            throw new IllegalArgumentException("Ungültiger Kurs: " + open + "/" + high + "/" + low + "/" + close);
        }
    }

    static Quote first(Day day, long price, long volume) {
        return new Quote(day, price, price, price, price, volume);
    }

    Quote with(long price, long quantity) {
        return new Quote(day, open, Math.max(high, price), Math.min(low, price), price, volume + quantity);
    }
}
