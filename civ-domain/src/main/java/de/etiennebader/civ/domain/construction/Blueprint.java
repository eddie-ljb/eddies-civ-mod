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

    /**
     * Die Haustür: Lage der unteren Türhälfte und Himmelsrichtung, in die sie nach außen zeigt.
     *
     * @param outward 0 Norden, 1 Osten, 2 Süden, 3 Westen (ungedrehter Plan)
     */
    public record Door(int x, int y, int z, int outward) {
    }

    /** Namensteile von Wassergebäuden: Sie dürfen auf dem Wasser stehen, alle anderen Gebäude nur an Land. */
    private static final List<String> WATER_WORDS = List.of("leuchtturm", "lighthouse", "schiff", "ship", "boot",
            "boat", "hafen", "harbor", "harbour", "steg", "pier", "werft", "shipyard", "bruecke", "bridge");

    /**
     * Ob es ein Wassergebäude ist (Leuchtturm, Schiff, Steg, Hafen, Brücke …): Nur solche Pläne werden auf dem Wasser
     * gebaut, alle anderen an Land. Erkannt am Namen oder am Stil {@code wasser}.
     */
    public boolean isWaterBuilding() {
        String lower = name.toLowerCase(Locale.ROOT);
        return style.equalsIgnoreCase("wasser") || style.equalsIgnoreCase("water")
                || WATER_WORDS.stream().anyMatch(lower::contains);
    }

    /** Die niedrigste Tür in der Außenwand; leer, wenn der Plan keine hat (Plätze, Brunnen, Parks). */
    public java.util.Optional<Door> door() {
        Door best = null;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == 0) {
                continue;
            }
            String state = palette.get(blocks[i]);
            if (!baseName(state).endsWith("_door") || !state.contains("half=lower")) {
                continue;
            }
            int x = i % sizeX;
            int z = (i / sizeX) % sizeZ;
            int y = i / (sizeX * sizeZ);
            int outward = z == 0 ? 0 : x == sizeX - 1 ? 1 : z == sizeZ - 1 ? 2 : x == 0 ? 3 : -1;
            if (outward >= 0 && (best == null || y < best.y())) {
                best = new Door(x, y, z, outward);
            }
        }
        return java.util.Optional.ofNullable(best);
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
            if (index != 0 && needsItem(palette.get(index))) {
                counts.merge(baseName(palette.get(index)), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Ob für den Block ein eigener Gegenstand verbraucht wird; die obere Türhälfte gehört zur unteren. */
    public static boolean needsItem(String state) {
        return !state.contains("half=upper");
    }

    /** Größe in Blöcken nach {@code quarters} Vierteldrehungen: bei ungeraden vertauschen sich X und Z. */
    public int footprintX(int quarters) {
        return quarters % 2 == 0 ? sizeX : sizeZ;
    }

    public int footprintZ(int quarters) {
        return quarters % 2 == 0 ? sizeZ : sizeX;
    }

    /** Ein Block nach {@code quarters} Vierteldrehungen im Uhrzeigersinn (von oben gesehen). */
    public Placement rotated(Placement p, int quarters) {
        return switch (quarters % 4) {
            case 1 -> new Placement(sizeZ - 1 - p.z(), p.y(), p.x(), p.state());
            case 2 -> new Placement(sizeX - 1 - p.x(), p.y(), sizeZ - 1 - p.z(), p.state());
            case 3 -> new Placement(p.z(), p.y(), sizeX - 1 - p.x(), p.state());
            default -> p;
        };
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
