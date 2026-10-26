package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität eines Villager-Individuums.
 */
public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "value");
    }

    public static PersonId generate(IdGenerator ids) {
        return new PersonId(ids.next());
    }
}
