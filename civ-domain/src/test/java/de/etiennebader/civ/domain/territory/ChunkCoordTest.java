package de.etiennebader.civ.domain.territory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static de.etiennebader.civ.domain.territory.DimensionId.NETHER;
import static de.etiennebader.civ.domain.territory.DimensionId.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkCoordTest {

    @Test
    @DisplayName("containing rechnet auch negative Blockkoordinaten richtig um")
    void containingHandlesNegativeCoordinates() {
        assertEquals(new ChunkCoord(OVERWORLD, 0, 0), ChunkCoord.containing(OVERWORLD, 15, 0));
        assertEquals(new ChunkCoord(OVERWORLD, 1, 0), ChunkCoord.containing(OVERWORLD, 16, 0));
        assertEquals(new ChunkCoord(OVERWORLD, -1, -1), ChunkCoord.containing(OVERWORLD, -1, -16));
        assertEquals(new ChunkCoord(OVERWORLD, -2, 0), ChunkCoord.containing(OVERWORLD, -17, 0));
    }

    @Test
    @DisplayName("neighbours liefert die vier Nachbarn")
    void neighboursReturnsFourChunks() {
        ChunkCoord origin = new ChunkCoord(OVERWORLD, 0, 0);

        assertEquals(Set.of(
                new ChunkCoord(OVERWORLD, 0, -1), new ChunkCoord(OVERWORLD, 0, 1),
                new ChunkCoord(OVERWORLD, -1, 0), new ChunkCoord(OVERWORLD, 1, 0)), origin.neighbours());
    }

    @Test
    @DisplayName("Angrenzend nur bei gemeinsamer Kante in derselben Dimension")
    void adjacencyRequiresSharedEdgeAndDimension() {
        ChunkCoord origin = new ChunkCoord(OVERWORLD, 0, 0);

        assertTrue(origin.isAdjacentTo(new ChunkCoord(OVERWORLD, 1, 0)));
        assertFalse(origin.isAdjacentTo(new ChunkCoord(OVERWORLD, 1, 1)));
        assertFalse(origin.isAdjacentTo(origin));
        assertFalse(origin.isAdjacentTo(new ChunkCoord(NETHER, 1, 0)));
    }

    @Test
    @DisplayName("Blockgrenzen eines Chunks")
    void blockBounds() {
        ChunkCoord chunk = new ChunkCoord(OVERWORLD, -1, 2);

        assertEquals(-16, chunk.minBlockX());
        assertEquals(-1, chunk.maxBlockX());
        assertEquals(32, chunk.minBlockZ());
        assertEquals(47, chunk.maxBlockZ());
    }
}
