package de.etiennebader.civ.domain.law;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Verzeichnis aller bekannten Gesetzesparameter. Übersetzt zwischen gespeicherter Form
 * (Parameter-ID und Wert als Text) und typisiertem {@link ParameterValue}.
 *
 * <p>Unterstützte Werttypen: {@code Long}, {@code Integer}, {@code Boolean}, {@code String}.
 * In Phase 3 kommen Parameter aus Datapacks hinzu.
 */
public final class ParameterCatalog {

    private final Map<String, ParameterKey<?>> keys = new LinkedHashMap<>();

    public ParameterCatalog(Collection<ParameterKey<?>> keys) {
        Objects.requireNonNull(keys, "keys");
        for (ParameterKey<?> key : keys) {
            if (this.keys.putIfAbsent(key.id(), key) != null) {
                throw new IllegalArgumentException("Parameter-ID doppelt: " + key.id());
            }
        }
    }

    /** Katalog mit allen {@link StandardParameters}. */
    public static ParameterCatalog standard() {
        return new ParameterCatalog(StandardParameters.all());
    }

    public Optional<ParameterKey<?>> find(String id) {
        return Optional.ofNullable(keys.get(Objects.requireNonNull(id, "id")));
    }

    /** Textform eines Werts, z. B. {@code 5} oder {@code true}. */
    public static String encode(ParameterValue<?> value) {
        return String.valueOf(value.value());
    }

    /**
     * Baut aus gespeicherter Form wieder einen typisierten Wert.
     *
     * @throws IllegalArgumentException bei unbekannter ID oder unlesbarem Wert
     */
    public ParameterValue<?> decode(String id, String encoded) {
        ParameterKey<?> key = find(id).orElseThrow(() -> new IllegalArgumentException("Unbekannter Parameter: " + id));
        return decode(key, encoded);
    }

    private static <T> ParameterValue<T> decode(ParameterKey<T> key, String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        Object parsed;
        try {
            parsed = parse(key.type(), encoded.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Wert '%s' passt nicht zu %s".formatted(encoded, key.id()), e);
        }
        return ParameterValue.of(key, key.type().cast(parsed));
    }

    private static Object parse(Class<?> type, String text) {
        if (type == Long.class) {
            return Long.valueOf(text);
        }
        if (type == Integer.class) {
            return Integer.valueOf(text);
        }
        if (type == Boolean.class) {
            if (!text.equals("true") && !text.equals("false")) {
                throw new NumberFormatException("Kein Wahrheitswert: " + text);
            }
            return Boolean.valueOf(text);
        }
        if (type == String.class) {
            return text;
        }
        throw new IllegalArgumentException("Nicht unterstützter Parametertyp: " + type.getName());
    }

    public List<ParameterKey<?>> keys() {
        return List.copyOf(keys.values());
    }
}
