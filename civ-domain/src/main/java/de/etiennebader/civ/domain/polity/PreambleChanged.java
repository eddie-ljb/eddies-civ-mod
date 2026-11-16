package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Der Entwurf der Verfassung hat eine neue Präambel.
 */
public record PreambleChanged(PolityId polity, String preamble, Day occurredOn) implements PolityEvent {

    public PreambleChanged {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(preamble, "preamble");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
