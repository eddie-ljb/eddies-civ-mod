package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Amt wurde besetzt, durch Wahl oder Ernennung.
 */
public record OfficeFilled(PolityId polity, Office office, ActorRef holder, Day occurredOn)
        implements PolityEvent {

    public OfficeFilled {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
