package de.etiennebader.civ.sim.governance;

import de.etiennebader.civ.domain.territory.ChunkCoord;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Feste Reihenfolge für Chunks. Der Kern liefert Gebiete als {@code Set} ohne garantierte
 * Reihenfolge; wer darüber iteriert, sortiert vorher, damit die Simulation deterministisch bleibt.
 */
public final class Chunks {

    public static final Comparator<ChunkCoord> ORDER = Comparator
            .comparing((ChunkCoord chunk) -> chunk.dimension().value())
            .thenComparingInt(ChunkCoord::x)
            .thenComparingInt(ChunkCoord::z);

    private Chunks() {
    }

    public static List<ChunkCoord> sorted(Collection<ChunkCoord> chunks) {
        return chunks.stream().sorted(ORDER).toList();
    }
}
