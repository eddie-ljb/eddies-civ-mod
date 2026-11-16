package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Ein Wahlplakat (Litfaßsäule) im Gebiet einer Gemeinde. Es wirbt für einen Kandidaten oder eine Partei bei den
 * Dorfbewohnern der Gemeinde.
 *
 * @param subject der beworbene Kandidat oder die Partei
 */
public record Poster(BlockPosition position, ActorRef subject, ActorRef owner, Day placedOn) {

    public Poster {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(placedOn, "placedOn");
    }
}
