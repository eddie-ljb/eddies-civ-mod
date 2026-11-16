package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Urteil.
 *
 * @param upheld    Anklage bzw. Klage hat Erfolg (schuldig, stattgegeben, nichtig)
 * @param fine      Geldstrafe an die Kasse des Gerichts in kleinster Einheit
 * @param damages   Schadenersatz an den Kläger in kleinster Einheit
 * @param exile     Verbannung aus der Polity des Gerichts
 * @param dismissal Amtsenthebung des Angeklagten
 * @param guiltyVotes   Stimmen der Geschworenen für schuldig (0 ohne Jury)
 * @param innocentVotes Stimmen der Geschworenen für nicht schuldig
 */
public record Verdict(boolean upheld, long fine, long damages, boolean exile, boolean dismissal, String reasoning,
                      ActorRef decidedBy, int guiltyVotes, int innocentVotes, Day day) {

    public Verdict {
        if (fine < 0 || damages < 0) {
            throw new IllegalArgumentException("Negative Strafe");
        }
        reasoning = Objects.requireNonNull(reasoning, "reasoning").strip();
        Objects.requireNonNull(decidedBy, "decidedBy");
        Objects.requireNonNull(day, "day");
    }
}
