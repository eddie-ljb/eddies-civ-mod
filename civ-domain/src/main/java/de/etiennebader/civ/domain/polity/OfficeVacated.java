package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Amt ist frei geworden, z. B. durch Entlassung oder Austritt des Inhabers.
 */
public record OfficeVacated(PolityId polity, Office office, ActorRef previousHolder, Day occurredOn)
        implements PolityEvent {

    public OfficeVacated {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(previousHolder, "previousHolder");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
