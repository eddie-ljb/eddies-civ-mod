package de.etiennebader.civ.fabric;

import de.etiennebader.civ.EddiesCivMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Macht aus hochgeladenen Logos (Wappen, Wahlplakat, Leuchtreklame) Karten, die jeder Spieler sieht, auch ohne
 * zusätzliche Dateien auf dem Client: Das PNG wird auf 128 × 128 eingepasst und auf die Kartenfarben abgebildet.
 * Je Bild entsteht genau eine gesperrte Karte; welches Bild welche Karte hat, steht in {@code maps.tsv} im Bilderordner.
 */
final class LogoMaps {

    private static final int SIZE = 128;
    private static final int[] PALETTE = new int[64 * 4];

    static {
        for (int base = 1; base < 64; base++) {
            MapColor color = MapColor.byId(base);
            for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
                int packed = base * 4 + brightness.id;
                PALETTE[packed] = color == null || color == MapColor.NONE ? 0
                        : MapColor.getColorFromPackedId(color.getPackedId(brightness) & 0xFF);
            }
        }
    }

    private final Map<String, Integer> mapIds = new HashMap<>();
    private Optional<Path> index = Optional.empty();

    /** Die Karte mit dem Bild; legt sie beim ersten Mal an. Leer, wenn die Datei fehlt oder kein PNG ist. */
    Optional<ItemStack> map(ServerLevel level, Path imagesDirectory, String hash) {
        loadIndex(imagesDirectory);
        Integer known = mapIds.get(hash);
        if (known != null && level.getMapData(new MapId(known)) != null) {
            return Optional.of(stack(known));
        }
        BufferedImage image;
        try {
            image = ImageIO.read(imagesDirectory.resolve(hash + ".png").toFile());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        if (image == null) {
            return Optional.empty();
        }
        MapId id = level.getFreeMapId();
        MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked();
        data.colors = quantize(image);
        level.setMapData(id, data);
        mapIds.put(hash, id.id());
        saveIndex();
        return Optional.of(stack(id.id()));
    }

    /**
     * Die Karte mit dem Bild, auf die ganze Fläche gestreckt (ohne Ränder), damit es als Flaggentuch, Wappenschild oder
     * Plakat ein Bauteil ganz ausfüllt.
     */
    Optional<ItemStack> filled(ServerLevel level, Path imagesDirectory, String hash) {
        return cached(level, Optional.of(imagesDirectory), hash + "#fill", () -> {
            try {
                BufferedImage image = ImageIO.read(imagesDirectory.resolve(hash + ".png").toFile());
                return image == null ? null : stretch(image);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        });
    }

    /** Eine einfarbige Karte, z. B. für leere oder einfarbige Flaggen. */
    ItemStack solid(ServerLevel level, Optional<Path> imagesDirectory, int rgb) {
        return cached(level, imagesDirectory, String.format("solid#%06x", rgb & 0xFFFFFF), () -> {
            byte[] colors = new byte[SIZE * SIZE];
            java.util.Arrays.fill(colors, nearest(rgb & 0xFFFFFF));
            return colors;
        }).orElseThrow();
    }

    private Optional<ItemStack> cached(ServerLevel level, Optional<Path> directory, String key,
                                       java.util.function.Supplier<byte[]> colors) {
        directory.ifPresent(this::loadIndex);
        Integer known = mapIds.get(key);
        if (known != null && level.getMapData(new MapId(known)) != null) {
            return Optional.of(stack(known));
        }
        byte[] data = colors.get();
        if (data == null) {
            return Optional.empty();
        }
        MapId id = level.getFreeMapId();
        MapItemSavedData saved = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked();
        saved.colors = data;
        level.setMapData(id, saved);
        if (directory.isPresent()) {
            mapIds.put(key, id.id());
            saveIndex();
        }
        return Optional.of(stack(id.id()));
    }

    private static byte[] stretch(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, SIZE, SIZE, null);
        graphics.dispose();
        byte[] colors = new byte[SIZE * SIZE];
        Map<Integer, Byte> cache = new HashMap<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int argb = scaled.getRGB(x, y);
                colors[y * SIZE + x] = (argb >>> 24) < 128 ? nearest(0xFFFFFF)
                        : cache.computeIfAbsent(argb & 0xFFFFFF, LogoMaps::nearest);
            }
        }
        return colors;
    }

    private static ItemStack stack(int id) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, new MapId(id));
        return stack;
    }

    /** Passt das Bild mittig und mit Seitenverhältnis in 128 × 128 ein und wählt je Punkt die nächste Kartenfarbe. */
    static byte[] quantize(BufferedImage source) {
        double scale = Math.min((double) SIZE / source.getWidth(), (double) SIZE / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage fitted = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = fitted.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, (SIZE - width) / 2, (SIZE - height) / 2, width, height, null);
        graphics.dispose();
        byte[] colors = new byte[SIZE * SIZE];
        Map<Integer, Byte> cache = new HashMap<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int argb = fitted.getRGB(x, y);
                if ((argb >>> 24) < 128) {
                    colors[y * SIZE + x] = 0; // durchsichtig
                    continue;
                }
                colors[y * SIZE + x] = cache.computeIfAbsent(argb & 0xFFFFFF, LogoMaps::nearest);
            }
        }
        return colors;
    }

    private static byte nearest(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        long best = Long.MAX_VALUE;
        int bestIndex = 4;
        for (int i = 4; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            if (color == 0) {
                continue;
            }
            int cr = color >> 16 & 0xFF;
            int cg = color >> 8 & 0xFF;
            int cb = color & 0xFF;
            long dr = r - cr;
            long dg = g - cg;
            long db = b - cb;
            long distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return (byte) bestIndex;
    }

    private void loadIndex(Path directory) {
        Path file = directory.resolve("maps.tsv");
        if (index.isPresent() && index.get().equals(file)) {
            return;
        }
        index = Optional.of(file);
        mapIds.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] f = line.split("\t");
                if (f.length == 2) {
                    mapIds.put(f[0], Integer.parseInt(f[1]));
                }
            }
        } catch (IOException | RuntimeException e) {
            EddiesCivMod.LOGGER.warn("Logo-Karten konnten nicht gelesen werden", e);
        }
    }

    private void saveIndex() {
        if (index.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        mapIds.forEach((hash, id) -> lines.add(hash + "\t" + id));
        try {
            Files.write(index.get(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            EddiesCivMod.LOGGER.warn("Logo-Karten konnten nicht gespeichert werden", e);
        }
    }
}
