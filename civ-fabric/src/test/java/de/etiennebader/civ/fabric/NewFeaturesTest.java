package de.etiennebader.civ.fabric;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NewFeaturesTest {

    @Test
    void englishTwinsOfNewWordsExist() {
        assertEquals("bless", EnglishCommands.translate("religion", "segnen"));
        assertEquals("scripture", EnglishCommands.translate("religion", "schrift"));
        assertEquals("roads", EnglishCommands.translate("strasse", "wege"));
        assertEquals("shovel", EnglishCommands.translate("bauen", "schaufel"));
    }

    @Test
    void logoQuantizingKeepsShapeAndTransparency() {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 32; y++) {
                image.setRGB(x, y, x < 32 ? 0xFFD02020 : 0xFF2040D0); // links rot, rechts blau
            }
        }
        byte[] colors = LogoMaps.quantize(image);
        assertEquals(128 * 128, colors.length);
        assertEquals(0, colors[0], "oben ist der Rand frei (Seitenverhältnis 2:1 bleibt erhalten)");
        byte left = colors[64 * 128 + 20];
        byte right = colors[64 * 128 + 108];
        assertNotEquals(0, left);
        assertNotEquals(0, right);
        assertNotEquals(left, right, "rot und blau werden verschiedene Kartenfarben");
    }

    @Test
    void grassGreenMapsToTheGrassMapColor() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, 0xFF7FB238);
            }
        }
        assertEquals(6, LogoMaps.quantize(image)[64 * 128 + 64], "Grasgrün ist Kartenfarbe 1 in der hellsten Stufe");
    }
}
