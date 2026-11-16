package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Das Parlament wurde aufgelöst, z. B. weil das Recht keine Sitze mehr vorsieht.
 */
public record ParliamentDissolved(PolityId polity, Day occurredOn) implements PolityEvent {

    public ParliamentDissolved {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
