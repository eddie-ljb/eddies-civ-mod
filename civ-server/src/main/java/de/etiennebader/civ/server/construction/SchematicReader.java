package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.construction.Blueprint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest Bauplandateien fremder Werkzeuge: Litematica ({@code .litematic}) und Sponge Schematic ({@code .schem},
 * Version 2 und 3; erzeugt z. B. von WorldEdit). Mehrere Regionen einer Litematica-Datei werden zu einem Quader
 * verschmolzen.
 */
public final class SchematicReader {

    private SchematicReader() {
    }

    /** Ungültige oder zu große Datei. */
    public static final class InvalidSchematicException extends Exception {
        InvalidSchematicException(String message) {
            super(message);
        }
    }

    /**
     * @param fileName Dateiname; die Endung bestimmt das Format
     * @param name     Planname im Spiel
     * @throws InvalidSchematicException wenn die Datei nicht lesbar ist oder das Maximalmaß überschreitet
     */
    public static Blueprint read(String fileName, String name, String style, byte[] bytes)
            throws InvalidSchematicException {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        try {
            Map<String, Object> root = Nbt.readGzip(new ByteArrayInputStream(bytes));
            if (lower.endsWith(".litematic")) {
                return litematic(root, name, style);
            }
            if (lower.endsWith(".schem")) {
                return sponge(root, name, style);
            }
            throw new InvalidSchematicException("Nur .litematic und .schem werden gelesen");
        } catch (IOException | ClassCastException | NullPointerException | IllegalArgumentException
                 | ArrayIndexOutOfBoundsException e) {
            throw new InvalidSchematicException("Bauplan nicht lesbar: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Litematica

    @SuppressWarnings("unchecked")
    private static Blueprint litematic(Map<String, Object> root, String name, String style)
            throws InvalidSchematicException {
        Map<String, Object> regions = (Map<String, Object>) root.get("Regions");
        if (regions == null || regions.isEmpty()) {
            throw new InvalidSchematicException("Litematica-Datei ohne Regionen");
        }
        record Region(int ox, int oy, int oz, int sx, int sy, int sz, List<String> palette, long[] states) {
        }
        List<Region> list = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Object value : regions.values()) {
            Map<String, Object> region = (Map<String, Object>) value;
            Map<String, Object> position = (Map<String, Object>) region.get("Position");
            Map<String, Object> size = (Map<String, Object>) region.get("Size");
            int sx = ((Number) size.get("x")).intValue();
            int sy = ((Number) size.get("y")).intValue();
            int sz = ((Number) size.get("z")).intValue();
            int ox = ((Number) position.get("x")).intValue() + (sx < 0 ? sx + 1 : 0);
            int oy = ((Number) position.get("y")).intValue() + (sy < 0 ? sy + 1 : 0);
            int oz = ((Number) position.get("z")).intValue() + (sz < 0 ? sz + 1 : 0);
            sx = Math.abs(sx);
            sy = Math.abs(sy);
            sz = Math.abs(sz);
            if (sx == 0 || sy == 0 || sz == 0) {
                continue;
            }
            List<String> palette = new ArrayList<>();
            for (Object entry : (List<Object>) region.get("BlockStatePalette")) {
                palette.add(state((Map<String, Object>) entry));
            }
            list.add(new Region(ox, oy, oz, sx, sy, sz, palette, (long[]) region.get("BlockStates")));
            minX = Math.min(minX, ox);
            minY = Math.min(minY, oy);
            minZ = Math.min(minZ, oz);
            maxX = Math.max(maxX, ox + sx);
            maxY = Math.max(maxY, oy + sy);
            maxZ = Math.max(maxZ, oz + sz);
        }
        if (list.isEmpty()) {
            throw new InvalidSchematicException("Litematica-Datei ohne Blöcke");
        }
        Builder builder = new Builder(maxX - minX, maxY - minY, maxZ - minZ);
        for (Region region : list) {
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(region.palette.size() - 1));
            long mask = (1L << bits) - 1;
            long total = (long) region.sx * region.sy * region.sz;
            for (int y = 0; y < region.sy; y++) {
                for (int z = 0; z < region.sz; z++) {
                    for (int x = 0; x < region.sx; x++) {
                        long index = (long) y * region.sx * region.sz + (long) z * region.sx + x;
                        long bitIndex = index * bits;
                        int word = (int) (bitIndex >>> 6);
                        int offset = (int) (bitIndex & 63);
                        long value = region.states[word] >>> offset;
                        if (offset + bits > 64) {
                            value |= region.states[word + 1] << (64 - offset);
                        }
                        int paletteIndex = (int) (value & mask);
                        builder.set(region.ox + x - minX, region.oy + y - minY, region.oz + z - minZ,
                                region.palette.get(paletteIndex));
                    }
                }
            }
            if (total <= 0) {
                throw new InvalidSchematicException("Leere Region");
            }
        }
        return builder.build(name, style);
    }

    // ---------------------------------------------------------------- Sponge

    @SuppressWarnings("unchecked")
    private static Blueprint sponge(Map<String, Object> root, String name, String style)
            throws InvalidSchematicException {
        Map<String, Object> schematic = root.containsKey("Schematic") ? (Map<String, Object>) root.get("Schematic")
                : root;
        int width = ((Number) schematic.get("Width")).intValue();
        int height = ((Number) schematic.get("Height")).intValue();
        int length = ((Number) schematic.get("Length")).intValue();
        Map<String, Object> blocks = schematic.containsKey("Blocks") ? (Map<String, Object>) schematic.get("Blocks")
                : schematic;
        Map<String, Object> paletteMap = (Map<String, Object>) blocks.get("Palette");
        byte[] data = (byte[]) (blocks.containsKey("Data") ? blocks.get("Data") : blocks.get("BlockData"));
        String[] palette = new String[paletteMap.size() + 1];
        for (Map.Entry<String, Object> entry : paletteMap.entrySet()) {
            palette[((Number) entry.getValue()).intValue()] = entry.getKey();
        }
        if (width < 1 || height < 1 || length < 1) {
            throw new InvalidSchematicException("Ungültige Größe");
        }
        Builder builder = new Builder(width, height, length);
        int position = 0;
        for (int i = 0; i < width * height * length; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                byte next = data[position++];
                value |= (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new InvalidSchematicException("Ungültige Blockdaten");
                }
            }
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);
            builder.set(x, y, z, palette[value]);
        }
        return builder.build(name, style);
    }

    @SuppressWarnings("unchecked")
    private static String state(Map<String, Object> entry) {
        String blockName = (String) entry.get("Name");
        Map<String, Object> properties = (Map<String, Object>) entry.get("Properties");
        if (properties == null || properties.isEmpty()) {
            return blockName;
        }
        StringBuilder text = new StringBuilder(blockName).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> property : new java.util.TreeMap<>(properties).entrySet()) {
            if (!first) {
                text.append(',');
            }
            text.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return text.append(']').toString();
    }

    /** Sammelt Blöcke in einem Quader und baut daraus die Palette mit Luft an Stelle 0. */
    private static final class Builder {
        private final int sx;
        private final int sy;
        private final int sz;
        private final List<String> palette = new ArrayList<>(List.of(Blueprint.AIR));
        private final Map<String, Integer> indexOf = new LinkedHashMap<>();
        private final int[] blocks;

        Builder(int sx, int sy, int sz) throws InvalidSchematicException {
            if (sx < 1 || sy < 1 || sz < 1 || sx > Blueprint.MAX_SIZE || sy > Blueprint.MAX_SIZE
                    || sz > Blueprint.MAX_SIZE) {
                throw new InvalidSchematicException("Zu groß: " + sx + "x" + sy + "x" + sz + " (höchstens "
                        + Blueprint.MAX_SIZE + " je Achse)");
            }
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.blocks = new int[sx * sy * sz];
            indexOf.put(Blueprint.AIR, 0);
        }

        void set(int x, int y, int z, String state) {
            if (Blueprint.isEmpty(state)) {
                return;
            }
            int index = indexOf.computeIfAbsent(state, key -> {
                palette.add(key);
                return palette.size() - 1;
            });
            blocks[(y * sz + z) * sx + x] = index;
        }

        Blueprint build(String name, String style) {
            return new Blueprint(name, style, 1, sx, sy, sz, palette, blocks);
        }
    }
}
