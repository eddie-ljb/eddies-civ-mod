package de.etiennebader.civ.domain.law;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Typisierter Schlüssel eines Gesetzesparameters. Verbindet ID, Typ, Standardwert und
 * die Regel für gültige Werte an einer Stelle.
 *
 * <p>Gleichheit nur über die {@link #id()}.
 *
 * @param <T> Typ des Parameterwerts
 */
public final class ParameterKey<T> {

    private final String id;
    private final Class<T> type;
    private final T defaultValue;
    private final Predicate<? super T> constraint;

    private ParameterKey(String id, Class<T> type, T defaultValue, Predicate<? super T> constraint) {
        this.id = requireValidId(id);
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.constraint = Objects.requireNonNull(constraint, "constraint");
        if (!accepts(defaultValue)) {
            throw new IllegalArgumentException("Standardwert verletzt die Regel von " + id + ": " + defaultValue);
        }
    }

    /** Parameter ohne Einschränkung des Wertebereichs. */
    public static <T> ParameterKey<T> of(String id, Class<T> type, T defaultValue) {
        return new ParameterKey<>(id, type, defaultValue, value -> true);
    }

    /** Parameter, dessen Werte die Regel {@code constraint} erfüllen müssen. */
    public static <T> ParameterKey<T> constrained(String id, Class<T> type, T defaultValue,
                                                  Predicate<? super T> constraint) {
        return new ParameterKey<>(id, type, defaultValue, constraint);
    }

    public String id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** Ob {@code value} den richtigen Typ hat und die Regel des Parameters erfüllt. */
    public boolean accepts(Object value) {
        return type.isInstance(value) && constraint.test(type.cast(value));
    }

    private static String requireValidId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Parameter-ID darf nicht leer sein");
        }
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ParameterKey<?> key && id.equals(key.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
