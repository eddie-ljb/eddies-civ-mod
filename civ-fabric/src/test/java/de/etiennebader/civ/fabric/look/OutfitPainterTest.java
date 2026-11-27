package de.etiennebader.civ.fabric.look;

import de.etiennebader.civ.domain.population.Occupation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitPainterTest {

    private static final int SKIN = 0xFFBE886C;
    private static final int GREY = 0xFF6F6D6A;

    /** Eine kleine Vanilla-Attrappe: Haut am Kopf und im Hals, graues Gewand an Körper und Beinen. */
    private static final class Image implements OutfitPainter.Canvas {

        final int[] pixels = new int[64 * 64];

        Image() {
            for (int y = 0; y < 18; y++) {
                for (int x = 0; x < 32; x++) {
                    pixels[y * 64 + x] = SKIN;
                }
            }
            for (int y = 20; y < 38; y++) {
                for (int x = 16; x < 44; x++) {
                    pixels[y * 64 + x] = x >= 23 && x < 27 && y < 29 ? SKIN : GREY;
                }
            }
        }

        @Override
        public int get(int x, int y) {
            return pixels[y * 64 + x];
        }

        @Override
        public void set(int x, int y, int argb) {
            assertTrue(x >= 0 && x < 64 && y >= 0 && y < 64, "außerhalb der Textur: " + x + "," + y);
            pixels[y * 64 + x] = argb;
        }
    }

    @Test
    void civilianKeepsTheVanillaTexture() {
        Image image = new Image();
        int[] before = image.pixels.clone();
        OutfitPainter.paint(image, Occupation.CIVILIAN);
        assertArrayEquals(before, image.pixels);
    }

    @Test
    void everyOccupationDressesTheClothButNotTheFaceOrNeck() {
        for (Occupation occupation : Occupation.values()) {
            if (!occupation.dressed()) {
                continue;
            }
            Image image = new Image();
            OutfitPainter.paint(image, occupation);
            assertNotEquals(GREY, image.get(33, 36), occupation + ": Gewand unverändert");
            assertEquals(SKIN, image.get(24, 22), occupation + ": Hals verändert");
            assertEquals(SKIN, image.get(11, 13), occupation + ": Gesicht verändert");
            assertEquals(0xFF000000, image.get(33, 36) & 0xFF000000, occupation + ": Gewand durchsichtig");
        }
    }

    @Test
    void occupationsLookDifferentFromEachOther() {
        java.util.Set<java.util.List<Integer>> looks = new java.util.HashSet<>();
        for (Occupation occupation : Occupation.values()) {
            if (occupation.dressed()) {
                Image image = new Image();
                OutfitPainter.paint(image, occupation);
                looks.add(java.util.Arrays.stream(image.pixels).boxed().toList());
            }
        }
        assertEquals(Occupation.values().length - 1, looks.size());
    }

    @Test
    void brightnessOfTheOriginalSurvivesTheRecolor() {
        assertTrue((OutfitPainter.shade(0xFF808080, 50) & 0xFF) < (OutfitPainter.shade(0xFF808080, 150) & 0xFF));
    }
}
