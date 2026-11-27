package de.etiennebader.civ.domain.police;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine angezeigte oder entdeckte Straftat.
 *
 * @param number  laufende Nummer je Gemeinde
 * @param suspect Täter
 * @param on      Tag der Tat
 * @param handled ob sie bereits bearbeitet (festgenommen oder eingestellt) wurde
 */
public record Offense(int number, ActorRef suspect, CrimeKind kind, Day on, boolean handled) {

    public Offense {
        Objects.requireNonNull(suspect, "suspect");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(on, "on");
    }

    public Offense markHandled() {
        return new Offense(number, suspect, kind, on, true);
    }
}
