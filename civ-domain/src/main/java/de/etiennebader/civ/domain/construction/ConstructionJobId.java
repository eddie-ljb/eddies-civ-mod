package de.etiennebader.civ.domain.construction;

import de.etiennebader.civ.domain.shared.IdGenerator;

import java.util.Objects;
import java.util.UUID;

/** Kennung eines Bauauftrags. */
public record ConstructionJobId(UUID value) {

    public ConstructionJobId {
        Objects.requireNonNull(value, "value");
    }

    public static ConstructionJobId generate(IdGenerator ids) {
        return new ConstructionJobId(ids.next());
    }
}
