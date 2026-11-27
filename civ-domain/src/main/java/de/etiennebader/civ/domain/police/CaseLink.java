package de.etiennebader.civ.domain.police;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;

/**
 * Verknüpft ein Strafverfahren des Gerichts mit der Festnahme, aus der es hervorging.
 *
 * @param caseNumber    Verfahrensnummer im Gericht
 * @param sentenceDays  vom Richter verhängte Haft in Tagen; 0 = nach Art der Tat ({@link CrimeKind#detentionDays()})
 * @param witnesses     Zeugen der Tat, die nach Eröffnung des Verfahrens geladen werden
 * @param prepared      ob Zeugen und Polizeibericht dem Verfahren schon hinzugefügt wurden
 */
public record CaseLink(int caseNumber, PolityId polity, ActorRef suspect, CrimeKind kind, int sentenceDays,
                       List<ActorRef> witnesses, boolean prepared) {

    public CaseLink {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(suspect, "suspect");
        Objects.requireNonNull(kind, "kind");
        if (sentenceDays < 0) {
            throw new IllegalArgumentException("Negative Haft");
        }
        witnesses = List.copyOf(witnesses);
    }

    public CaseLink withSentence(int days) {
        return new CaseLink(caseNumber, polity, suspect, kind, days, witnesses, prepared);
    }

    public CaseLink markPrepared() {
        return new CaseLink(caseNumber, polity, suspect, kind, sentenceDays, witnesses, true);
    }

    public int effectiveDays() {
        return sentenceDays > 0 ? sentenceDays : kind.detentionDays();
    }
}
