package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.List;
import java.util.Objects;

/**
 * Ein beschlossenes Gesetz, das auf die Unterschrift des Oberhaupts wartet. Es tritt in Kraft, wenn das Oberhaupt
 * unterzeichnet oder die Frist verstreicht; mit Veto verfällt es.
 *
 * @param kind     {@code CHANGE}, {@code ENACT} oder {@code REPEAL}
 * @param title    Titel bzw. Regelungsschlüssel zur Anzeige
 * @param text     Freitext (nur {@code ENACT})
 * @param values   Regelungen als {@code schluessel=kodierter Wert}
 * @param statute  aufzuhebender Paragraph (nur {@code REPEAL})
 * @param proposer Antragsteller
 * @param deadline letzter Tag; danach gilt das Gesetz auch ohne Unterschrift
 */
public record PendingLaw(String kind, String title, String text, List<String> values, String statute,
                         ActorRef proposer, Day acceptedOn, Day deadline) {

    public PendingLaw {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(statute, "statute");
        Objects.requireNonNull(proposer, "proposer");
        values = List.copyOf(values);
    }
}
