package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität einer Wahl für ein Amt.
 */
public record ElectionId(UUID value) {

    public ElectionId {
        Objects.requireNonNull(value, "value");
    }

    public static ElectionId generate(IdGenerator ids) {
        return new ElectionId(ids.next());
    }
}
