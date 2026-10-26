package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.territory.ChunkCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static de.etiennebader.civ.domain.territory.DimensionId.NETHER;
import static de.etiennebader.civ.domain.territory.DimensionId.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaTest {

    @Test
    @DisplayName("Fläche zählt beide Grenzen mit")
    void sizeIncludesBothBounds() {
        assertEquals(100, new Area(OVERWORLD, 0, 0, 9, 9).size());
        assertEquals(1, new Area(OVERWORLD, 5, 5, 5, 5).size());
        assertEquals(50, new Area(OVERWORLD, -5, -10, -1, -1).size());
    }

    @Test
    @DisplayName("Untere Grenze über der oberen ist ungültig")
    void rejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> new Area(OVERWORLD, 10, 0, 9, 9));
    }

    @Test
    @DisplayName("spanning normalisiert beliebige Eckpunkte")
    void spanningNormalisesCorners() {
        assertEquals(new Area(OVERWORLD, 0, 0, 9, 9), Area.spanning(OVERWORLD, 9, 0, 0, 9));
    }

    @Test
    @DisplayName("Überlappung: gemeinsamer Block ja, nur angrenzend nein, andere Dimension nein")
    void overlaps() {
        Area area = new Area(OVERWORLD, 0, 0, 9, 9);

        assertTrue(area.overlaps(new Area(OVERWORLD, 9, 9, 20, 20)));
        assertTrue(area.overlaps(new Area(OVERWORLD, 2, 2, 3, 3)));
        assertFalse(area.overlaps(new Area(OVERWORLD, 10, 0, 20, 9)));
        assertFalse(area.overlaps(new Area(NETHER, 0, 0, 9, 9)));
    }

    @Test
    @DisplayName("contains prüft Grenzen und Dimension")
    void containsChecksBoundsAndDimension() {
        Area area = new Area(OVERWORLD, 0, 0, 9, 9);

        assertTrue(area.contains(OVERWORLD, 0, 9));
        assertFalse(area.contains(OVERWORLD, 10, 9));
        assertFalse(area.contains(NETHER, 5, 5));
    }

    @Test
    @DisplayName("Betroffene Chunks, auch über Chunk-Grenzen und im Negativen")
    void chunksAcrossBoundaries() {
        assertEquals(Set.of(new ChunkCoord(OVERWORLD, 0, 0)), new Area(OVERWORLD, 0, 0, 15, 15).chunks());
        assertEquals(Set.of(
                        new ChunkCoord(OVERWORLD, -1, 0), new ChunkCoord(OVERWORLD, 0, 0),
                        new ChunkCoord(OVERWORLD, -1, 1), new ChunkCoord(OVERWORLD, 0, 1)),
                new Area(OVERWORLD, -2, 10, 3, 20).chunks());
    }

    @Test
    @DisplayName("touches erkennt berührte Chunks")
    void touchesDetectsChunks() {
        Area area = new Area(OVERWORLD, 14, 0, 17, 3);

        assertTrue(area.touches(new ChunkCoord(OVERWORLD, 0, 0)));
        assertTrue(area.touches(new ChunkCoord(OVERWORLD, 1, 0)));
        assertFalse(area.touches(new ChunkCoord(OVERWORLD, 2, 0)));
    }
}
