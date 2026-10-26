package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität eines Gesetzes.
 */
public record StatuteId(UUID value) {

    public StatuteId {
        Objects.requireNonNull(value, "value");
    }

    public static StatuteId generate(IdGenerator ids) {
        return new StatuteId(ids.next());
    }
}
