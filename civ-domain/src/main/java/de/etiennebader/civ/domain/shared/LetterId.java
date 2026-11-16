package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität eines Briefs.
 */
public record LetterId(UUID value) {

    public LetterId {
        Objects.requireNonNull(value, "value");
    }

    public static LetterId generate(IdGenerator ids) {
        return new LetterId(ids.next());
    }
}
