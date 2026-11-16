package de.etiennebader.civ.server;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Nachricht an alle Spieler, an die Mitglieder einer Gemeinde oder an einen einzelnen Spieler.
 *
 * @param town      leer für alle Spieler (oder einen einzelnen Empfänger)
 * @param recipient der einzelne Empfänger, falls die Nachricht nur ihm gilt
 */
public record Announcement(Optional<PolityId> town, Message message, Optional<PlayerRef> recipient) {

    public Announcement(Optional<PolityId> town, Message message) {
        this(town, message, Optional.empty());
    }

    public Announcement {
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(recipient, "recipient");
    }

    /** Nur an diesen Spieler, wenn er online ist. */
    public static Announcement toPlayer(PlayerRef player, Message message) {
        return new Announcement(Optional.empty(), message, Optional.of(player));
    }

    public static Announcement toAll(Message message) {
        return new Announcement(Optional.empty(), message);
    }

    public static Announcement toTown(PolityId town, Message message) {
        return new Announcement(Optional.of(town), message);
    }
}
