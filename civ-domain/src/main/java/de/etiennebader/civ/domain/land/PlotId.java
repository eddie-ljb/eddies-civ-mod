package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.IdGenerator;

import java.util.Objects;
import java.util.UUID;

/**
 * Identität eines Grundstücks.
 */
public record PlotId(UUID value) {

    public PlotId {
        Objects.requireNonNull(value, "value");
    }

    public static PlotId generate(IdGenerator ids) {
        return new PlotId(ids.next());
    }
}
