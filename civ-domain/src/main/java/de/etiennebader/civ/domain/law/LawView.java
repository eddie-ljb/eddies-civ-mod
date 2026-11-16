package de.etiennebader.civ.domain.law;

import java.util.List;
import java.util.Optional;

/**
 * Lesender Zugriff auf das geltende Recht einer Polity.
 */
public interface LawView {

    /**
     * Der geltende Wert eines Parameters: der Wert des zuletzt erlassenen geltenden Gesetzes,
     * das ihn setzt, sonst der Standardwert.
     */
    <T> T valueOf(ParameterKey<T> key);

    /** Alle geltenden Gesetze in der Reihenfolge ihres Erlasses. */
    List<EnactedStatute> inForce();

    /** Der Wert, den ein geltendes Gesetz ausdrücklich setzt; leer, wenn der Standardwert gilt. */
    default <T> Optional<T> explicitValueOf(ParameterKey<T> key) {
        List<EnactedStatute> statutes = inForce();
        for (int index = statutes.size() - 1; index >= 0; index--) {
            Optional<T> value = statutes.get(index).statute().valueOf(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
