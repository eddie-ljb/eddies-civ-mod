package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.construction.Blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Vordefinierte Gebäude in verschiedenen Stilen: je Stil eine Hütte, ein Haus und ein Mehrfamilienhaus. Sie werden
 * beim Start erzeugt, brauchen also keine Dateien.
 */
public final class BuiltInBlueprints {

    /** Ein Baustil mit seinen Baustoffen. */
    private record Style(String id, String wall, String corner, String floor, String roof, String door) {
    }

    private static final List<Style> STYLES = List.of(
            new Style("holz", "minecraft:oak_planks", "minecraft:oak_log", "minecraft:spruce_planks",
                    "minecraft:oak_slab", "minecraft:oak_door"),
            new Style("stein", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone",
                    "minecraft:stone_brick_slab", "minecraft:iron_door"),
            new Style("ziegel", "minecraft:bricks", "minecraft:stone_bricks", "minecraft:smooth_stone",
                    "minecraft:brick_slab", "minecraft:spruce_door"));

    private BuiltInBlueprints() {
    }

    /** Alle eingebauten Pläne. */
    public static List<Blueprint> all() {
        List<Blueprint> plans = new ArrayList<>();
        for (Style style : STYLES) {
            plans.add(house(style, style.id() + "-huette", 5, 5, 1, 1));
            plans.add(house(style, style.id() + "-haus", 7, 7, 2, 1));
            plans.add(house(style, style.id() + "-mehrfamilienhaus", 11, 7, 3, 4));
        }
        return plans;
    }

    private static Blueprint house(Style style, String name, int width, int depth, int floors, int units) {
        final int floorHeight = 4;
        int height = floors * floorHeight + 1;
        List<String> palette = new ArrayList<>(List.of(Blueprint.AIR, style.wall(), style.corner(), style.floor(),
                style.roof(), "minecraft:glass", style.door() + "[half=lower]", style.door() + "[half=upper]"));
        final int wall = 1;
        final int corner = 2;
        final int floor = 3;
        final int roof = 4;
        final int glass = 5;
        final int doorLower = 6;
        final int doorUpper = 7;
        int[] blocks = new int[width * height * depth];
        for (int level = 0; level < floors; level++) {
            int base = level * floorHeight;
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    blocks[(base * depth + z) * width + x] = floor;
                    boolean edgeX = x == 0 || x == width - 1;
                    boolean edgeZ = z == 0 || z == depth - 1;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    for (int dy = 1; dy < floorHeight; dy++) {
                        int block = edgeX && edgeZ ? corner : wall;
                        if (!(edgeX && edgeZ) && dy == 2 && (edgeX ? z : x) % 2 == 0) {
                            block = glass;
                        }
                        blocks[((base + dy) * depth + z) * width + x] = block;
                    }
                }
            }
        }
        int doorX = width / 2;
        blocks[(1 * depth + 0) * width + doorX] = doorLower;
        blocks[(2 * depth + 0) * width + doorX] = doorUpper;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                blocks[((height - 1) * depth + z) * width + x] = roof;
            }
        }
        return new Blueprint(name, style.id(), units, width, height, depth, palette, blocks);
    }
}
