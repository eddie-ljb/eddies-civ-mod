package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.land.PlotType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vordefinierte Gebäude in verschiedenen Stilen. Je Stil gibt es Wohnhäuser (Hütte, Haus, Mehrfamilienhaus), Gewerbe
 * (Laden, Gasthaus, Kontor), Industrie (Werkstatt, Lagerhalle), Landwirtschaft (Scheune, Stall) und kommunale Bauten
 * (Rathaus, Bibliothek, Feuerwache, Marktplatz, Brunnen, Pavillon, Park, Schule, Kapelle). Sie werden beim Start erzeugt,
 * brauchen also keine Dateien. {@link #plotTypesOf} sagt, auf welche Grundstücksarten ein Plan gehört; so wählen
 * Dorfbewohner, Firmen und Gemeinden passende Gebäude.
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

    /** Planart (Teil des Namens nach dem Stil) mit den Grundstücksarten, auf die sie gehört. */
    private static final Map<String, Set<PlotType>> KINDS = Map.ofEntries(
            Map.entry("huette", Set.of(PlotType.RESIDENTIAL)),
            Map.entry("haus", Set.of(PlotType.RESIDENTIAL)),
            Map.entry("mehrfamilienhaus", Set.of(PlotType.RESIDENTIAL)),
            Map.entry("laden", Set.of(PlotType.COMMERCIAL)),
            Map.entry("gasthaus", Set.of(PlotType.COMMERCIAL)),
            Map.entry("kontor", Set.of(PlotType.COMMERCIAL)),
            Map.entry("werkstatt", Set.of(PlotType.INDUSTRIAL)),
            Map.entry("lagerhalle", Set.of(PlotType.INDUSTRIAL, PlotType.COMMERCIAL)),
            Map.entry("scheune", Set.of(PlotType.AGRICULTURAL)),
            Map.entry("stall", Set.of(PlotType.AGRICULTURAL)),
            Map.entry("rathaus", Set.of(PlotType.PUBLIC)),
            Map.entry("bibliothek", Set.of(PlotType.PUBLIC)),
            Map.entry("feuerwache", Set.of(PlotType.PUBLIC)),
            Map.entry("marktplatz", Set.of(PlotType.PUBLIC)),
            Map.entry("brunnen", Set.of(PlotType.PUBLIC)),
            Map.entry("pavillon", Set.of(PlotType.PUBLIC)),
            Map.entry("park", Set.of(PlotType.PUBLIC)),
            Map.entry("schule", Set.of(PlotType.EDUCATION)),
            Map.entry("kapelle", Set.of(PlotType.RELIGIOUS)));

    /** Alle eingebauten Pläne. */
    public static List<Blueprint> all() {
        List<Blueprint> plans = new ArrayList<>();
        for (Style style : STYLES) {
            String id = style.id();
            plans.add(house(style, id + "-huette", 5, 5, 1, 1, 4));
            plans.add(house(style, id + "-haus", 7, 7, 2, 1, 4));
            plans.add(house(style, id + "-mehrfamilienhaus", 11, 7, 3, 4, 4));
            plans.add(house(style, id + "-laden", 9, 7, 1, 1, 4));
            plans.add(house(style, id + "-gasthaus", 11, 9, 2, 1, 4));
            plans.add(house(style, id + "-kontor", 9, 9, 2, 1, 4));
            plans.add(house(style, id + "-werkstatt", 9, 9, 1, 1, 5));
            plans.add(house(style, id + "-lagerhalle", 13, 9, 1, 1, 6));
            plans.add(house(style, id + "-scheune", 11, 9, 1, 1, 7));
            plans.add(house(style, id + "-stall", 9, 7, 1, 1, 4));
            plans.add(house(style, id + "-rathaus", 13, 11, 2, 1, 5));
            plans.add(house(style, id + "-bibliothek", 11, 9, 2, 1, 5));
            plans.add(house(style, id + "-feuerwache", 9, 9, 1, 1, 6));
            plans.add(house(style, id + "-schule", 13, 9, 1, 1, 5));
            plans.add(house(style, id + "-kapelle", 7, 11, 1, 1, 8));
            plans.add(plaza(style, id + "-marktplatz", 13));
            plans.add(well(style, id + "-brunnen"));
            plans.add(pavilion(style, id + "-pavillon"));
            plans.add(park(style, id + "-park"));
        }
        return plans;
    }

    /**
     * Auf welche Grundstücksarten ein eingebauter Plan gehört; leer bei importierten Plänen, die auf jedes Grundstück
     * passen, wenn ein Spieler sie bestellt, die aber niemand von selbst auswählt.
     */
    public static Set<PlotType> plotTypesOf(Blueprint plan) {
        int dash = plan.name().indexOf('-');
        if (dash < 0 || STYLES.stream().noneMatch(style -> style.id().equals(plan.name().substring(0, dash)))) {
            return Set.of();
        }
        return KINDS.getOrDefault(plan.name().substring(dash + 1), Set.of());
    }

    /** Ein offener Platz: Steinplatten mit Marktständen (Pfosten mit Dach) rundherum. */
    private static Blueprint plaza(Style style, String name, int size) {
        Grid grid = new Grid(size, 4, size);
        grid.fill(0, 0, 0, size - 1, 0, size - 1, "minecraft:stone_bricks");
        grid.fill(2, 0, 2, size - 3, 0, size - 3, "minecraft:smooth_stone");
        for (int x = 1; x < size - 1; x += 4) {
            for (int z : new int[] {1, size - 2}) {
                grid.fill(x, 1, z, x, 2, z, style.corner());
                grid.fill(x + 2, 1, z, x + 2, 2, z, style.corner());
                grid.fill(x, 3, z, x + 2, 3, z, style.roof());
            }
        }
        grid.set(size / 2, 1, size / 2, "minecraft:lantern");
        return grid.build(name, style.id(), 1);
    }

    /** Ein Ziehbrunnen: gemauerter Ring, Wasser, zwei Pfosten und ein kleines Dach. */
    private static Blueprint well(Style style, String name) {
        Grid grid = new Grid(5, 5, 5);
        grid.fill(0, 0, 0, 4, 0, 4, "minecraft:cobblestone");
        grid.shell(0, 1, 0, 4, 1, 4, "minecraft:stone_bricks");
        grid.fill(1, 1, 1, 3, 1, 3, "minecraft:water");
        grid.fill(0, 2, 2, 0, 3, 2, style.corner());
        grid.fill(4, 2, 2, 4, 3, 2, style.corner());
        grid.fill(0, 4, 1, 4, 4, 3, style.roof());
        return grid.build(name, style.id(), 1);
    }

    /** Ein Pavillon: vier Pfosten, ein Dach und eine Bank. */
    private static Blueprint pavilion(Style style, String name) {
        Grid grid = new Grid(7, 5, 7);
        grid.fill(0, 0, 0, 6, 0, 6, style.floor());
        for (int x : new int[] {0, 6}) {
            for (int z : new int[] {0, 6}) {
                grid.fill(x, 1, z, x, 3, z, style.corner());
            }
        }
        grid.fill(0, 4, 0, 6, 4, 6, style.roof());
        grid.set(3, 1, 3, "minecraft:lantern");
        return grid.build(name, style.id(), 1);
    }

    /** Ein Park: Rasen, Hecken am Rand, Laternen und ein Weg in der Mitte. */
    private static Blueprint park(Style style, String name) {
        Grid grid = new Grid(11, 3, 11);
        grid.fill(0, 0, 0, 10, 0, 10, "minecraft:grass_block");
        grid.fill(5, 0, 0, 5, 0, 10, "minecraft:dirt_path");
        grid.fill(0, 0, 5, 10, 0, 5, "minecraft:dirt_path");
        grid.shell(0, 1, 0, 10, 1, 10, "minecraft:oak_leaves[persistent=true]");
        grid.fill(5, 1, 0, 5, 1, 0, Blueprint.AIR);
        for (int x : new int[] {2, 8}) {
            for (int z : new int[] {2, 8}) {
                grid.set(x, 1, z, style.corner());
                grid.set(x, 2, z, "minecraft:lantern");
            }
        }
        return grid.build(name, style.id(), 1);
    }

    /** Ein Quader aus Palettenindizes, in den Bauwerke Schicht für Schicht gesetzt werden. */
    private static final class Grid {

        private final int width;
        private final int height;
        private final int depth;
        private final int[] blocks;
        private final List<String> palette = new ArrayList<>(List.of(Blueprint.AIR));

        Grid(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.blocks = new int[width * height * depth];
        }

        void set(int x, int y, int z, String state) {
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
                return;
            }
            int index = palette.indexOf(state);
            if (index < 0) {
                palette.add(state);
                index = palette.size() - 1;
            }
            blocks[(y * depth + z) * width + x] = index;
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    for (int x = x1; x <= x2; x++) {
                        set(x, y, z, state);
                    }
                }
            }
        }

        /** Nur der Rand des Rechtecks, in jeder Ebene von {@code y1} bis {@code y2}. */
        void shell(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    for (int x = x1; x <= x2; x++) {
                        if (x == x1 || x == x2 || z == z1 || z == z2) {
                            set(x, y, z, state);
                        }
                    }
                }
            }
        }

        Blueprint build(String name, String style, int units) {
            return new Blueprint(name, style, units, width, height, depth, palette, blocks);
        }
    }

    private static Blueprint house(Style style, String name, int width, int depth, int floors, int units,
                                   int floorHeight) {
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
