package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/** Eine Verbannung: Der Akteur darf der Polity bis einschließlich {@code until} nicht beitreten. */
public record Exile(PolityId polity, ActorRef actor, Day until) {

    public Exile {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(until, "until");
    }
}
