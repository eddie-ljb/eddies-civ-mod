package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Richtung oder Wappen einer Partei haben sich geändert.
 */
public record PartyProfileChanged(PolityId polity, PartyProfile profile, Day occurredOn) implements PolityEvent {

    public PartyProfileChanged {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
