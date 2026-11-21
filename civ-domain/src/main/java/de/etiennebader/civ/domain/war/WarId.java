package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.IdGenerator;

import java.util.Objects;
import java.util.UUID;

/** Identität eines Kriegs zwischen zwei Polities. */
public record WarId(UUID value) {

    public WarId {
        Objects.requireNonNull(value, "value");
    }

    public static WarId generate(IdGenerator ids) {
        return new WarId(ids.next());
    }
}
