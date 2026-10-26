package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.InvalidValueException;

import java.util.Objects;

/**
 * Name einer Polity, ohne führende und folgende Leerzeichen.
 */
public record PolityName(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 32;

    public PolityName {
        Objects.requireNonNull(value, "value");
        String trimmed = value.strip();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new InvalidValueException(ErrorCode.INVALID_POLITY_NAME, value, MIN_LENGTH, MAX_LENGTH);
        }
        value = trimmed;
    }

    public static PolityName of(String value) {
        return new PolityName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
