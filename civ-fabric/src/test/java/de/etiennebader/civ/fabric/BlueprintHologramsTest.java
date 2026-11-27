package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.construction.Blueprint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintHologramsTest {

    private static Blueprint solid(int size) {
        int[] blocks = new int[size * size * size];
        Arrays.fill(blocks, 1);
        return new Blueprint("kiste", "holz", 1, size, size, size, List.of(Blueprint.AIR, "minecraft:stone"), blocks);
    }

    @Test
    @DisplayName("Das Innere eines Quaders bekommt kein Hologramm, nur die Hülle")
    void onlyTheShellIsShown() {
        assertEquals(26, BlueprintHolograms.visible(solid(3)).size());
        assertEquals(1, BlueprintHolograms.visible(solid(1)).size());
    }

    @Test
    @DisplayName("Sehr große Pläne werden gleichmäßig auf die Höchstzahl ausgedünnt")
    void hugePlansAreThinned() {
        List<Blueprint.Placement> shown = BlueprintHolograms.visible(solid(30));
        assertEquals(BlueprintHolograms.MAX_BLOCKS, shown.size());
        assertTrue(shown.stream().anyMatch(block -> block.y() == 0) && shown.stream().anyMatch(block -> block.y() == 29));
    }

    @Test
    @DisplayName("Luft wird nie angezeigt")
    void airIsSkipped() {
        int[] blocks = new int[2 * 2 * 2];
        blocks[0] = 1;
        Blueprint plan = new Blueprint("eins", "holz", 1, 2, 2, 2, List.of(Blueprint.AIR, "minecraft:stone"), blocks);
        assertEquals(1, BlueprintHolograms.visible(plan).size());
    }
}
