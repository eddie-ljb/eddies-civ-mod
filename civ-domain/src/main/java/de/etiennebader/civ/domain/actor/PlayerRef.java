package de.etiennebader.civ.domain.actor;

import de.etiennebader.civ.domain.shared.PlayerId;

import java.util.Objects;
import java.util.UUID;

/**
 * Ein echter Spieler.
 */
public record PlayerRef(PlayerId id) implements ActorRef {

    public PlayerRef {
        Objects.requireNonNull(id, "id");
    }

    public static PlayerRef of(UUID minecraftUuid) {
        return new PlayerRef(new PlayerId(minecraftUuid));
    }

    @Override
    public UUID uuid() {
        return id.value();
    }
}
