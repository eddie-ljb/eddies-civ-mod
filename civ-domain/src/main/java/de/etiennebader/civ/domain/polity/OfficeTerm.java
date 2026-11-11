package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Amtszeit.
 *
 * @param until letzter Tag der Amtszeit; leer, wenn das Amt unbefristet ist
 */
public record OfficeTerm(Office office, ActorRef holder, Day since, Optional<Day> until) {

    public OfficeTerm {
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(until, "until");
        if (until.isPresent() && until.get().isBefore(since)) {
            throw new IllegalArgumentException("Amtszeit endet vor ihrem Beginn: " + since + " bis " + until.get());
        }
    }

    /** Ob die Amtszeit an diesem Tag abgelaufen ist. */
    public boolean expiredOn(Day day) {
        return until.isPresent() && !day.isBefore(until.get());
    }
}
