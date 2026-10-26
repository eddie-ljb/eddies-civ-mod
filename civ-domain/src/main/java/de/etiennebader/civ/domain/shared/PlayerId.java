package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität eines Spielers; entspricht seiner Minecraft-UUID.
 */
public record PlayerId(UUID value) {

    public PlayerId {
        Objects.requireNonNull(value, "value");
    }

    public static PlayerId generate(IdGenerator ids) {
        return new PlayerId(ids.next());
    }
}
