package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.InvalidValueException;

import java.util.Objects;

/**
 * Ein von einem Gesetz gesetzter Parameterwert.
 *
 * @param <T> Typ des Parameterwerts
 */
public record ParameterValue<T>(ParameterKey<T> key, T value) {

    public ParameterValue {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!key.accepts(value)) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER_VALUE, key.id(), value);
        }
    }

    public static <T> ParameterValue<T> of(ParameterKey<T> key, T value) {
        return new ParameterValue<>(key, value);
    }
}
