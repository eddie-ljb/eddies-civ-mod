package de.etiennebader.civ.domain.law;

import java.util.List;

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
}
