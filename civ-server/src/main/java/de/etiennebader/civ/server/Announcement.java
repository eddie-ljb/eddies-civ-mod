package de.etiennebader.civ.server;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Nachricht an alle Spieler oder nur an die Mitglieder einer Gemeinde.
 *
 * @param town leer für alle Spieler
 */
public record Announcement(Optional<PolityId> town, Message message) {

    public Announcement {
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(message, "message");
    }

    public static Announcement toAll(Message message) {
        return new Announcement(Optional.empty(), message);
    }

    public static Announcement toTown(PolityId town, Message message) {
        return new Announcement(Optional.of(town), message);
    }
}
