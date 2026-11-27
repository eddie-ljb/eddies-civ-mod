package de.etiennebader.civ.domain.construction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintDoorTest {

    private static Blueprint withDoorAt(int x, int z, int sizeX, int sizeZ) {
        int[] blocks = new int[sizeX * 3 * sizeZ];
        blocks[(1 * sizeZ + z) * sizeX + x] = 1;
        blocks[(2 * sizeZ + z) * sizeX + x] = 2;
        return new Blueprint("tuer-test", "holz", 1, sizeX, 3, sizeZ,
                List.of(Blueprint.AIR, "minecraft:oak_door[half=lower]", "minecraft:oak_door[half=upper]"), blocks);
    }

    @Test
    @DisplayName("Die Tür wird mit ihrer Außenrichtung gefunden")
    void findsDoorAndDirection() {
        assertEquals(new Blueprint.Door(2, 1, 0, 0), withDoorAt(2, 0, 5, 5).door().orElseThrow());
        assertEquals(2, withDoorAt(2, 4, 5, 5).door().orElseThrow().outward());
        assertEquals(1, withDoorAt(4, 2, 5, 5).door().orElseThrow().outward());
        assertEquals(3, withDoorAt(0, 2, 5, 5).door().orElseThrow().outward());
    }

    @Test
    @DisplayName("Ohne Tür in der Außenwand gibt es keine Tür")
    void noDoor() {
        assertEquals(Optional.empty(), withDoorAt(2, 2, 5, 5).door());
        assertTrue(new Blueprint("leer", "holz", 1, 2, 2, 2, List.of(Blueprint.AIR), new int[8]).door().isEmpty());
    }
}
