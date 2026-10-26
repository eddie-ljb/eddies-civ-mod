package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität einer Polity (Gemeinde, später auch Partei, Firma, ...).
 */
public record PolityId(UUID value) {

    public PolityId {
        Objects.requireNonNull(value, "value");
    }

    public static PolityId generate(IdGenerator ids) {
        return new PolityId(ids.next());
    }
}
