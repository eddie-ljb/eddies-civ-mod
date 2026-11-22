package de.etiennebader.civ.application.directory;

import de.etiennebader.civ.domain.actor.PlayerRef;

import de.etiennebader.civ.domain.population.Gender;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Spieler, der schon einmal auf dem Server war, mit seinem zuletzt bekannten Namen und, falls angegeben, seinem Geschlecht (#20).
 */
public record KnownPlayer(PlayerRef player, String name, Optional<Gender> gender) {

    public KnownPlayer(PlayerRef player, String name) {
        this(player, name, Optional.empty());
    }

    public KnownPlayer {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(gender, "gender");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Spielername darf nicht leer sein");
        }
    }
}
