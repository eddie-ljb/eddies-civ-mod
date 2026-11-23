package de.etiennebader.civ.domain.production;

import java.util.Objects;

/** Position eines Maschinenblocks in der Welt. */
public record MachinePos(String dimension, int x, int y, int z) {

    public MachinePos {
        Objects.requireNonNull(dimension, "dimension");
    }
}
