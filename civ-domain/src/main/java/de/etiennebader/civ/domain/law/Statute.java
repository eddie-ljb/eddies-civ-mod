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
 * Ein Paragraph eines Gesetzes: Titel, optional Freitext und die Regelungen, die er setzt. {@code lawName} und
 * {@code partName} ordnen ihn einem benannten Gesetz und darin einem benannten Teil zu (beide leer = ohne
 * Zuordnung). Absätze, Sätze und Aufzählungen ergeben sich aus dem Text, siehe {@link LawText}. Ein Gesetz braucht
 * mindestens eine Regelung oder einen Text; Text ohne erkannte Regelung gilt, wird aber nicht automatisch
 * durchgesetzt.
 *
 * @param text Freitext, höchstens {@link #MAX_TEXT_LENGTH} Zeichen; leer bei reinen Regelungsgesetzen
 */
public record Statute(StatuteId id, String lawName, String partName, String title, String text,
                      List<ParameterValue<?>> values) {

    public static final int MIN_TITLE_LENGTH = 3;
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_TEXT_LENGTH = 1000;

    /** Ohne Freitext. */
    public Statute(StatuteId id, String title, List<ParameterValue<?>> values) {
        this(id, title, "", values);
    }

    /** Ohne Gesetz und Teil. */
    public Statute(StatuteId id, String title, String text, List<ParameterValue<?>> values) {
        this(id, "", "", title, text, values);
    }

    public Statute {
        Objects.requireNonNull(id, "id");
        lawName = requireValidName(lawName);
        partName = requireValidName(partName);
        if (lawName.isEmpty() && !partName.isEmpty()) {
            throw new InvalidValueException(ErrorCode.INVALID_STATUTE_TITLE, partName, MIN_TITLE_LENGTH,
                    MAX_TITLE_LENGTH);
        }
        title = requireValidTitle(title);
        text = Objects.requireNonNull(text, "text").strip();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new InvalidValueException(ErrorCode.STATUTE_TEXT_TOO_LONG, text.length(), MAX_TEXT_LENGTH);
        }
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (values.isEmpty() && text.isEmpty()) {
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

    /** Name eines Gesetzes oder Teils: leer oder wie ein Titel. */
    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "name");
        return name.isBlank() ? "" : requireValidTitle(name);
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
