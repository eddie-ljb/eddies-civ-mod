package de.etiennebader.civ.domain.religion;

import de.etiennebader.civ.domain.shared.IdGenerator;

import java.util.Objects;
import java.util.UUID;

/**
 * Kennung einer Religion.
 */
public record ReligionId(UUID value) {

    public ReligionId {
        Objects.requireNonNull(value, "value");
    }

    public static ReligionId generate(IdGenerator ids) {
        return new ReligionId(ids.next());
    }
}
