package de.etiennebader.civ.application.directory;

import de.etiennebader.civ.domain.actor.PlayerRef;

import java.util.Objects;

/**
 * Ein Spieler, der schon einmal auf dem Server war, mit seinem zuletzt bekannten Namen.
 */
public record KnownPlayer(PlayerRef player, String name) {

    public KnownPlayer {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Spielername darf nicht leer sein");
        }
    }
}
