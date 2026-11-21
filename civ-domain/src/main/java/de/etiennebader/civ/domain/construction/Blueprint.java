package de.etiennebader.civ.domain.construction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Ein Bauplan: ein Quader aus Blockzuständen, relativ zu einer Ecke. Quelle sind eingebaute Stile oder importierte
 * Dateien (Litematica, Sponge Schematic).
 *
 * <p>Die Blöcke liegen in der Reihenfolge {@code (y * sizeZ + z) * sizeX + x} vor; gebaut wird von unten nach oben,
 * damit Bauwerke nie in der Luft hängen.
 *
 * @param name    eindeutiger Name, z. B. {@code holz-haus}
 * @param style   Stil, z. B. {@code holz}
 * @param units   Wohneinheiten; mehr als 1 bei Mehrfamilienhäusern
 * @param palette Blockzustände als Text, z. B. {@code minecraft:oak_door[half=lower]}; Eintrag 0 ist Luft
 * @param blocks  Palettenindex je Position
 */
public record Blueprint(String name, String style, int units, int sizeX, int sizeY, int sizeZ, List<String> palette,
                        int[] blocks) {

    public static final String AIR = "minecraft:air";
    /** Größtes zugelassenes Maß je Achse, damit niemand den Server mit einem Plan lahmlegt. */
    public static final int MAX_SIZE = 64;

    /** Ein zu setzender Block, relativ zur Ecke des Plans. */
    public record Placement(int x, int y, int z, String state) {
    }

    public Blueprint {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(style, "style");
        if (!name.matches("[a-z0-9_\\-]{1,40}")) {
            throw new IllegalArgumentException("Planname: nur a-z, 0-9, _ und -, höchstens 40 Zeichen: " + name);
        }
        if (units < 1) {
            throw new IllegalArgumentException("units");
        }
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > MAX_SIZE || sizeY > MAX_SIZE || sizeZ > MAX_SIZE) {
            throw new IllegalArgumentException("Größe " + sizeX + "x" + sizeY + "x" + sizeZ + " (höchstens "
                    + MAX_SIZE + " je Achse)");
        }
        palette = List.copyOf(palette);
        if (palette.isEmpty() || !AIR.equals(palette.getFirst())) {
            throw new IllegalArgumentException("Palette beginnt mit Luft");
        }
        if (blocks.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Blockzahl passt nicht zur Größe");
        }
        for (int index : blocks) {
            if (index < 0 || index >= palette.size()) {
                throw new IllegalArgumentException("Palettenindex " + index);
            }
        }
        blocks = blocks.clone();
    }

    @Override
    public int[] blocks() {
        return blocks.clone();
    }

    /** Anzahl der zu setzenden (nicht leeren) Blöcke. */
    public int blockCount() {
        int count = 0;
        for (int index : blocks) {
            if (index != 0) {
                count++;
            }
        }
        return count;
    }

    /** Alle zu setzenden Blöcke von unten nach oben. */
    public List<Placement> placements() {
        List<Placement> list = new ArrayList<>(blockCount());
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = blocks[(y * sizeZ + z) * sizeX + x];
                    if (index != 0) {
                        list.add(new Placement(x, y, z, palette.get(index)));
                    }
                }
            }
        }
        return list;
    }

    /** Die zu setzenden Blöcke Nummer {@code from} bis {@code from + count - 1}, von unten nach oben. */
    public List<Placement> placements(int from, int count) {
        List<Placement> list = new ArrayList<>(Math.max(0, count));
        int seen = 0;
        for (int i = 0; i < blocks.length && list.size() < count; i++) {
            if (blocks[i] != 0) {
                if (seen >= from) {
                    list.add(new Placement(i % sizeX, i / (sizeX * sizeZ), (i / sizeX) % sizeZ,
                            palette.get(blocks[i])));
                }
                seen++;
            }
        }
        return list;
    }

    /** Wie viele Blöcke jeder Art (Blockname ohne Zustand) gebraucht werden. */
    public Map<String, Integer> materials() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index : blocks) {
            if (index != 0) {
                counts.merge(baseName(palette.get(index)), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** {@code minecraft:oak_door[half=lower]} wird zu {@code minecraft:oak_door}. */
    public static String baseName(String state) {
        int bracket = state.indexOf('[');
        return (bracket < 0 ? state : state.substring(0, bracket)).toLowerCase(Locale.ROOT);
    }

    /** Ob der Zustand "nichts setzen" bedeutet: Luft und Strukturleere aus fremden Dateien. */
    public static boolean isEmpty(String state) {
        String base = baseName(state);
        return base.equals(AIR) || base.equals("minecraft:cave_air") || base.equals("minecraft:void_air")
                || base.equals("minecraft:structure_void");
    }
}
