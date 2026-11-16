package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.LetterId;

import java.util.Objects;

/**
 * Ein Brief. Die Post stellt ihn am Folgetag in den Briefkasten des Empfängers zu.
 *
 * @param deliverOn ab diesem Tag liegt er im Briefkasten
 */
public record Letter(LetterId id, PlayerRef from, PlayerRef to, String subject, String body, Day sentOn,
                     Day deliverOn, boolean read) {

    public static final int MAX_SUBJECT = 48;
    public static final int MAX_BODY = 2000;

    public Letter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sentOn, "sentOn");
        Objects.requireNonNull(deliverOn, "deliverOn");
        subject = subject.strip();
        body = body.strip();
        if (subject.isEmpty() || subject.length() > MAX_SUBJECT || body.length() > MAX_BODY) {
            throw new IllegalArgumentException("Betreff 1 bis " + MAX_SUBJECT + ", Text bis " + MAX_BODY + " Zeichen");
        }
    }

    public boolean deliveredBy(Day day) {
        return !day.isBefore(deliverOn);
    }

    Letter markedRead() {
        return new Letter(id, from, to, subject, body, sentOn, deliverOn, true);
    }
}
