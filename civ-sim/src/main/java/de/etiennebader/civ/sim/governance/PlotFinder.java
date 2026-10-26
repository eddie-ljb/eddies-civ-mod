package de.etiennebader.civ.sim.governance;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.territory.ChunkCoord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sucht im Gebiet einer Gemeinde eine freie rechteckige Fläche für ein neues Grundstück.
 *
 * <p>Chunks werden in fester Reihenfolge geprüft, innerhalb eines Chunks zeilenweise in einem
 * Raster von {@value #STEP} Blöcken („first fit“). Dadurch liegen Grundstücke dicht beieinander.
 * Grundstücke liegen hier immer innerhalb eines Chunks; der Kern selbst erlaubt auch
 * chunkübergreifende Grundstücke.
 */
public final class PlotFinder {

    static final int STEP = 2;

    public Optional<Area> find(LandView land, Collection<ChunkCoord> territory, int width, int depth) {
        if (width < 1 || depth < 1 || width > ChunkCoord.SIZE || depth > ChunkCoord.SIZE) {
            throw new IllegalArgumentException("Ungültige Grundstücksgröße %d × %d".formatted(width, depth));
        }
        Map<ChunkCoord, List<Area>> occupiedByChunk = occupiedByChunk(land);
        for (ChunkCoord chunk : Chunks.sorted(territory)) {
            List<Area> occupied = occupiedByChunk.getOrDefault(chunk, List.of());
            for (int z = chunk.minBlockZ(); z + depth - 1 <= chunk.maxBlockZ(); z += STEP) {
                for (int x = chunk.minBlockX(); x + width - 1 <= chunk.maxBlockX(); x += STEP) {
                    Area candidate = new Area(chunk.dimension(), x, z, x + width - 1, z + depth - 1);
                    if (occupied.stream().noneMatch(candidate::overlaps)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Map<ChunkCoord, List<Area>> occupiedByChunk(LandView land) {
        Map<ChunkCoord, List<Area>> occupied = new HashMap<>();
        for (Plot plot : land.plots()) {
            for (ChunkCoord chunk : plot.area().chunks()) {
                occupied.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(plot.area());
            }
        }
        return occupied;
    }
}
