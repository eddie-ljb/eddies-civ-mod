package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.InvalidValueException;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Ein Gesetz, das einen oder mehrere Parameter setzt.
 */
public record Statute(StatuteId id, String title, List<ParameterValue<?>> values) {

    public static final int MIN_TITLE_LENGTH = 3;
    public static final int MAX_TITLE_LENGTH = 80;

    public Statute {
        Objects.requireNonNull(id, "id");
        title = requireValidTitle(title);
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (values.isEmpty()) {
            throw new InvalidValueException(ErrorCode.STATUTE_WITHOUT_VALUES);
        }
        requireUniqueKeys(values);
    }

    public static Statute of(StatuteId id, String title, ParameterValue<?>... values) {
        return new Statute(id, title, List.of(values));
    }

    /** Der Wert, den dieses Gesetz für {@code key} setzt, falls es ihn setzt. */
    public <T> Optional<T> valueOf(ParameterKey<T> key) {
        return values.stream()
                .filter(value -> value.key().equals(key))
                .findFirst()
                .map(value -> key.type().cast(value.value()));
    }

    private static String requireValidTitle(String title) {
        Objects.requireNonNull(title, "title");
        String trimmed = title.strip();
        if (trimmed.length() < MIN_TITLE_LENGTH || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidValueException(ErrorCode.INVALID_STATUTE_TITLE, title, MIN_TITLE_LENGTH, MAX_TITLE_LENGTH);
        }
        return trimmed;
    }

    private static void requireUniqueKeys(List<ParameterValue<?>> values) {
        Set<ParameterKey<?>> seen = new HashSet<>();
        for (ParameterValue<?> value : values) {
            if (!seen.add(value.key())) {
                throw new InvalidValueException(ErrorCode.DUPLICATE_PARAMETER, value.key().id());
            }
        }
    }
}
