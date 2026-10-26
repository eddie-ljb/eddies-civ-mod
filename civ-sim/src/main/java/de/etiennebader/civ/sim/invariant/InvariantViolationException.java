package de.etiennebader.civ.sim.invariant;

import de.etiennebader.civ.domain.shared.Day;

import java.util.List;

/**
 * Mindestens eine Invariante ist verletzt. Enthält den Tag und alle gefundenen Verstöße.
 */
public final class InvariantViolationException extends RuntimeException {

    private final Day day;
    private final List<String> violations;

    public InvariantViolationException(Day day, List<String> violations) {
        super("Invarianten an Tag " + day.value() + " verletzt:\n  - " + String.join("\n  - ", violations));
        this.day = day;
        this.violations = List.copyOf(violations);
    }

    public Day day() {
        return day;
    }

    public List<String> violations() {
        return violations;
    }
}
