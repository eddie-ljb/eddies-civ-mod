package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Grundstück wurde aufgelöst; seine Fläche ist wieder Gemeindeland.
 */
public record PlotDissolved(PolityId polity, PlotId plot, ActorRef lastHolder, Day occurredOn) implements LandEvent {

    public PlotDissolved {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(lastHolder, "lastHolder");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
