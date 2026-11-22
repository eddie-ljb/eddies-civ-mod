package de.etiennebader.civ.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.domain.population.Gender;
import de.etiennebader.civ.domain.population.VillagerIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Individuelle Dorfbewohner-Skins (#20): Hautton, Haarfarbe und Frisur folgen Geschlecht und UUID des
 * Dorfbewohners ({@link VillagerIdentity#lookOf}). Die Texturen entstehen zur Laufzeit aus der Vanilla-Textur
 * (nur Umfärben und Haare dazumalen), damit der Mod keine fremden Texturen mitliefert und Resource Packs, die
 * {@code villager.png} ersetzen, weiter wirken. Beruf und Biom zeichnen die Vanilla-Ebenen darüber.
 */
public final class VillagerSkins {

    private static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    // Multiplikatoren je Hautton auf die Vanilla-Hautfarbe.
    private static final float[][] SKIN = {
            {1.17F, 1.20F, 1.22F}, {1.0F, 1.0F, 1.0F}, {0.93F, 0.92F, 0.88F}, {0.72F, 0.74F, 0.80F},
            {0.52F, 0.56F, 0.64F}};
    private static final int[] HAIR = {
            0xFF231C1A, 0xFF462D1E, 0xFF6E4628, 0xFF8C3C23, 0xFFD6B464, 0xFFB05428, 0xFFAAAAA8};

    private static final Map<Long, Identifier> CACHE = new HashMap<>();

    private VillagerSkins() {
    }

    /** Textur dieses Dorfbewohners; leer, wenn sich die Vanilla-Textur nicht lesen lässt. */
    public static Optional<Identifier> textureOf(UUID uuid) {
        VillagerIdentity.Look look = VillagerIdentity.lookOf(uuid);
        long key = ((look.gender().ordinal() * 8L + look.skin()) * 16L + look.hair()) * 8L + look.style();
        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(BASE);
             NativeImage image = NativeImage.read(in)) {
            paint(image, look);
            Identifier id = EddiesCivMod.id("villager/look_" + key);
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "eddies-civ-mod villager " + key, copy(image)));
            CACHE.put(key, id);
            return Optional.of(id);
        } catch (Exception | LinkageError e) {
            CACHE.put(key, BASE);
            return Optional.of(BASE);
        }
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage target = new NativeImage(source.getWidth(), source.getHeight(), false);
        target.copyFrom(source);
        return target;
    }

    static void paint(NativeImage image, VillagerIdentity.Look look) {
        tintSkin(image, SKIN[look.skin()]);
        paintHair(image, look, HAIR[look.hair()]);
    }

    /** Färbt nur hautfarbene Pixel um; das graue Gewand bleibt, wie es ist. */
    private static void tintSkin(NativeImage image, float[] factor) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getPixel(x, y);
                int alpha = argb >>> 24;
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                if (alpha == 0 || !(r > g && g > b && r - b >= 40)) {
                    continue;
                }
                image.setPixel(x, y, alpha << 24 | scale(r, factor[0]) << 16 | scale(g, factor[1]) << 8
                        | scale(b, factor[2]));
            }
        }
    }

    private static int scale(int value, float factor) {
        return Math.min(255, Math.round(value * factor));
    }

    /**
     * Malt Haare auf den Kopf. UV-Layout des Kopfes (8x10x8): oben (8..16, 0..8), rechts (0..8, 8..18),
     * vorn (8..16, 8..18), links (16..24, 8..18), hinten (24..32, 8..18).
     */
    private static void paintHair(NativeImage image, VillagerIdentity.Look look, int color) {
        boolean female = look.gender() == Gender.FEMALE;
        // {Haarlänge hinten, Haarlänge an den Seiten, Stirnfransen} in Zeilen ab Kopfoberkante
        int[][] female_styles = {{10, 8, 0}, {10, 10, 3}, {7, 5, 2}};
        int[][] male_styles = {{5, 3, 1}, {2, 1, 0}, {7, 5, 2}};
        int[] style = (female ? female_styles : male_styles)[look.style()];
        fill(image, 8, 0, 16, 8, color, 0);
        fill(image, 24, 8, 32, 8 + style[0], color, 1);
        fill(image, 8, 8, 16, 8 + style[2], color, 2);
        for (int row = 8; row < 8 + style[1]; row++) {
            // Die beiden Spalten neben dem Gesicht bleiben ab Augenhöhe frei.
            boolean face = row >= 11;
            fill(image, 0, row, face ? 6 : 8, row + 1, color, row);
            fill(image, face ? 18 : 16, row, 24, row + 1, color, row);
        }
    }

    private static void fill(NativeImage image, int x0, int y0, int x1, int y1, int color, int seed) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int shade = (x * 7 + y * 13 + seed) % 3 * 8;
                int r = Math.max(0, (color >> 16 & 0xFF) - shade);
                int g = Math.max(0, (color >> 8 & 0xFF) - shade);
                int b = Math.max(0, (color & 0xFF) - shade);
                image.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
    }
}
