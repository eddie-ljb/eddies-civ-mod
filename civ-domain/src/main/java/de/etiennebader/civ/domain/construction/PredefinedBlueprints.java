package de.etiennebader.civ.domain.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vordefinierte Baupläne, die ohne eigenen Editor sofort nutzbar sind (#19: "vordefinierte Gebäude in verschiedenen
 * Stilen"). Einfache, schnell erkennbare Formen aus Vanilla-Blöcken; Türen/Fenster sind als offene Lücken gebaut,
 * nicht als eigentliche Tür- bzw. Fensterblöcke (siehe docs/issue-19-bauarbeiter-villager.md).
 */
public final class PredefinedBlueprints {

    private PredefinedBlueprints() {
    }

    /** Kleine Holzhütte: 5×5 Grundfläche, drei Stockwerke hoch, mit Eingang und Dach. */
    public static final Blueprint HUETTE = new Blueprint("huette", "Holzhütte", huette());

    /** Wachturm: 3×3 Schacht, acht Blöcke hoch, mit Fackeln und flachem Dach. */
    public static final Blueprint WACHTURM = new Blueprint("wachturm", "Wachturm", wachturm());

    public static final List<Blueprint> ALL = List.of(HUETTE, WACHTURM);

    public static Map<String, Blueprint> byId() {
        return ALL.stream().collect(java.util.stream.Collectors.toMap(Blueprint::id, b -> b, (a, b) -> a,
                java.util.LinkedHashMap::new));
    }

    private static List<BlockPlacement> huette() {
        List<BlockPlacement> blocks = new ArrayList<>();
        String wall = "minecraft:oak_planks";
        // Fundament
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new BlockPlacement(x, 0, z, wall));
            }
        }
        // Wände, zwei Stockwerke hoch, mit Eingang bei x=2,z=0
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    boolean border = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean entrance = y == 1 && x == 2 && z == 0;
                    if (border && !entrance) {
                        blocks.add(new BlockPlacement(x, y, z, wall));
                    }
                }
            }
        }
        // Dach
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new BlockPlacement(x, 3, z, wall));
            }
        }
        return blocks;
    }

    private static List<BlockPlacement> wachturm() {
        List<BlockPlacement> blocks = new ArrayList<>();
        String wall = "minecraft:cobblestone";
        // Fundament
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(new BlockPlacement(x, 0, z, wall));
            }
        }
        // Schacht, sieben Stockwerke hoch, mit Eingang bei x=1,z=0 im untersten Stockwerk
        for (int y = 1; y <= 7; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    boolean border = x == 0 || x == 2 || z == 0 || z == 2;
                    boolean entrance = y == 1 && x == 1 && z == 0;
                    if (border && !entrance) {
                        blocks.add(new BlockPlacement(x, y, z, wall));
                    }
                }
            }
        }
        // Fackeln auf halber Höhe
        blocks.add(new BlockPlacement(1, 4, 0, "minecraft:torch"));
        blocks.add(new BlockPlacement(1, 4, 2, "minecraft:torch"));
        // Flaches Dach
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(new BlockPlacement(x, 8, z, wall));
            }
        }
        return blocks;
    }
}
