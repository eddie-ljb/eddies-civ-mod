package de.etiennebader.civ.domain.police;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.List;
import java.util.Objects;

/**
 * Eine angezeigte oder entdeckte Straftat.
 *
 * @param number    laufende Nummer je Gemeinde
 * @param suspect   Täter
 * @param on        Tag der Tat
 * @param handled   ob sie bereits bearbeitet (festgenommen oder eingestellt) wurde
 * @param witnesses Zeugen, die die Tat gesehen haben und anzeigten (Dorfbewohner oder Spieler); sagen später vor Gericht aus
 */
public record Offense(int number, ActorRef suspect, CrimeKind kind, Day on, boolean handled,
                      List<ActorRef> witnesses) {

    public static final int MAX_WITNESSES = 3;

    public Offense {
        Objects.requireNonNull(suspect, "suspect");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(on, "on");
        witnesses = List.copyOf(witnesses);
        if (witnesses.size() > MAX_WITNESSES) {
            throw new IllegalArgumentException("Höchstens " + MAX_WITNESSES + " Zeugen");
        }
    }

    public Offense markHandled() {
        return new Offense(number, suspect, kind, on, true, witnesses);
    }
}
