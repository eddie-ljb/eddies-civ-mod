package de.etiennebader.civ.domain.construction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintWaterTest {

    private static Blueprint plan(String name, String style) {
        return new Blueprint(name, style, 1, 1, 1, 1, List.of(Blueprint.AIR, "minecraft:stone"), new int[] {1});
    }

    @Test
    @DisplayName("Leuchttürme, Schiffe und Stege sind Wassergebäude, Häuser nicht")
    void waterBuildingsAreRecognised() {
        assertTrue(plan("leuchtturm", "wasser").isWaterBuilding());
        assertTrue(plan("schiff", "holz").isWaterBuilding());
        assertTrue(plan("mein-steg", "holz").isWaterBuilding());
        assertTrue(plan("harbor-crane", "holz").isWaterBuilding());
        assertTrue(plan("irgendwas", "wasser").isWaterBuilding());
        assertFalse(plan("holz-haus", "holz").isWaterBuilding());
        assertFalse(plan("stein-kapelle", "stein").isWaterBuilding());
    }
}
