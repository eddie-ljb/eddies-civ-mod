package de.etiennebader.civ.domain.police;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine Person in einer Zelle, in Untersuchungshaft (ohne Urteil, {@code until} nur vorläufig) oder aus Urteil.
 */
public record Detention(ActorRef person, Day since, Day until, boolean remand) {

    public Detention {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(until, "until");
    }
}
