package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Chatnachricht: öffentlich (ohne Empfänger) oder direkt an einen Freund.
 *
 * @param seq    fortlaufende Nummer; Leser holen ab einer Nummer neue Nachrichten
 * @param at     Zeitpunkt in Millisekunden seit 1970
 * @param origin wo sie geschrieben wurde: {@code game} oder {@code web}
 */
public record ChatMessage(long seq, PlayerRef from, Optional<PlayerRef> to, String text, long at, String origin) {

    public static final int MAX_TEXT = 256;

    public ChatMessage {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(origin, "origin");
        text = text.strip();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Nachricht muss 1 bis " + MAX_TEXT + " Zeichen haben");
        }
    }

    public boolean direct() {
        return to.isPresent();
    }

    /** Ob ein Spieler sie lesen darf: öffentliche alle, direkte nur Absender und Empfänger. */
    public boolean visibleTo(PlayerRef reader) {
        return to.isEmpty() || from.equals(reader) || to.get().equals(reader);
    }
}
