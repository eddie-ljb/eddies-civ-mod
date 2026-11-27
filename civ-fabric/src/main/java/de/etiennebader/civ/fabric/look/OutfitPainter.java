package de.etiennebader.civ.fabric.look;

import de.etiennebader.civ.domain.population.Occupation;

/**
 * Malt Berufskleidung auf die 64x64-Textur eines Dorfbewohners: Das graue Gewand von Körper und Beinen wird in der
 * Farbe des Berufs eingefärbt (Falten und Schatten bleiben), dazu kommen Gürtel, Krawatte, Abzeichen, Streifen und ein
 * Hut auf dem Kopf. Haut, Gesicht und Nase bleiben unberührt. Die Klasse kennt keine Minecraft-Typen, damit man sie
 * testen und die Ergebnisse als Bild ansehen kann; der Client reicht seine {@code NativeImage} über {@link Canvas} durch.
 *
 * <p>UV-Layout der Vanilla-Textur: Kopf oben links (Oberseite 8..16 x 0..8, Seiten und Vorderseite ab Zeile 8), Körper
 * ab (16,20) mit Vorderseite (22..30, 26..38), Beine ab (0,22).
 */
public final class OutfitPainter {

    /** Zugriff auf die Pixel einer Textur im Format ARGB. */
    public interface Canvas {

        int get(int x, int y);

        void set(int x, int y, int argb);
    }

    /** Hutformen. */
    enum Hat { NONE, CAP, PEAKED, HARD_HAT, FEDORA, TOP_HAT, STRAW, BEANIE }

    /** Gewandfarbe, Gürtel, Hut und Schmuck eines Berufs. */
    record Outfit(int robe, int belt, Hat hat, int hatColor, int hatTrim, int accent, Mark mark) {
    }

    /** Zusätzliche Muster auf dem Gewand. */
    enum Mark { NONE, TIE, BADGE, STRIPES, VEST, PINSTRIPE, CHECK, COLLAR, CAMO, EPAULETTES, CARD }

    private static final int BASE_BRIGHTNESS = 100;

    private OutfitPainter() {
    }

    /** Malt die Kleidung; ohne Beruf ({@link Occupation#CIVILIAN}) bleibt die Textur, wie sie ist. */
    public static void paint(Canvas canvas, Occupation occupation) {
        if (!occupation.dressed()) {
            return;
        }
        Outfit outfit = outfitOf(occupation);
        recolorCloth(canvas, 16, 20, 44, 38, outfit);
        recolorCloth(canvas, 0, 22, 16, 38, outfit);
        decorate(canvas, outfit);
        hat(canvas, outfit);
    }

    static Outfit outfitOf(Occupation occupation) {
        return switch (occupation) {
            case POLITICIAN -> new Outfit(0xFF22304A, 0xFF111827, Hat.NONE, 0, 0, 0xFFB3202A, Mark.TIE);
            case JUDGE -> new Outfit(0xFF1B1B20, 0xFF0A0A0C, Hat.NONE, 0, 0, 0xFFF2F2F2, Mark.COLLAR);
            case OFFICER -> new Outfit(0xFF3F5B34, 0xFF2A2210, Hat.PEAKED, 0xFF2F4527, 0xFFD6B240, 0xFFD6B240,
                    Mark.EPAULETTES);
            case SOLDIER -> new Outfit(0xFF5C6B3A, 0xFF2D2A1B, Hat.CAP, 0xFF4B5A2E, 0xFF303A1E, 0xFF3A4420, Mark.CAMO);
            case POLICE -> new Outfit(0xFF1F3A68, 0xFF0F1B33, Hat.PEAKED, 0xFF16294D, 0xFF0B1226, 0xFFD6B240,
                    Mark.BADGE);
            case BANKER -> new Outfit(0xFF2B2B2E, 0xFF121214, Hat.TOP_HAT, 0xFF151517, 0xFFD6B240, 0xFFD6B240,
                    Mark.PINSTRIPE);
            case ENTREPRENEUR -> new Outfit(0xFF8A6A45, 0xFF3A2A18, Hat.FEDORA, 0xFF3C2B1C, 0xFF1E5E3A, 0xFF1E8A4C,
                    Mark.TIE);
            case JOURNALIST -> new Outfit(0xFFA89574, 0xFF4A3B26, Hat.FEDORA, 0xFF6B5A3C, 0xFF2B2B2B, 0xFFF4F4F4,
                    Mark.CARD);
            case BUILDER -> new Outfit(0xFF6B6F78, 0xFF2D2F36, Hat.HARD_HAT, 0xFFF2C21C, 0xFFD39A0A, 0xFFF26B1D,
                    Mark.VEST);
            case MECHANIC -> new Outfit(0xFF2F5FA8, 0xFF1B2F54, Hat.CAP, 0xFF2F5FA8, 0xFF1B2F54, 0xFFE0E0E0,
                    Mark.STRIPES);
            case MINER -> new Outfit(0xFF4E4538, 0xFF241E16, Hat.HARD_HAT, 0xFF8C8C92, 0xFF55555B, 0xFFF2C21C,
                    Mark.STRIPES);
            case LUMBERJACK -> new Outfit(0xFFA3302B, 0xFF2B1A12, Hat.BEANIE, 0xFF2D5A34, 0xFF1D3A22, 0xFF1D1D1D,
                    Mark.CHECK);
            case FARMER -> new Outfit(0xFF6F8F3C, 0xFF4A3520, Hat.STRAW, 0xFFD9B95A, 0xFFB28F34, 0xFF8A6A33,
                    Mark.VEST);
            case FISHER -> new Outfit(0xFFD9A81E, 0xFF5A4410, Hat.BEANIE, 0xFF2D4F8A, 0xFF1D3560, 0xFF2D4F8A,
                    Mark.STRIPES);
            case WORKER -> new Outfit(0xFF58748E, 0xFF2B3744, Hat.NONE, 0, 0, 0xFFB8C4CF, Mark.STRIPES);
            case CIVILIAN -> throw new IllegalArgumentException("Kein Beruf: " + occupation);
        };
    }

    /** Färbt graue Gewandpixel um; Haut (Hände, Hals) und Löcher bleiben. Gürtel werden zur Gürtelfarbe. */
    private static void recolorCloth(Canvas canvas, int x0, int y0, int x1, int y1, Outfit outfit) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int argb = canvas.get(x, y);
                if (argb >>> 24 == 0) {
                    continue;
                }
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                int brightness = (r + g + b) / 3;
                boolean grey = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 14 && brightness > 40;
                boolean belt = y >= 34 && r > b && r - b < 40 && brightness < 80 && brightness > 30;
                if (grey) {
                    canvas.set(x, y, shade(outfit.robe(), brightness));
                } else if (belt) {
                    canvas.set(x, y, shade(outfit.belt(), brightness * 2));
                }
            }
        }
    }

    /** Farbe, so hell wie die Vorlage im Verhältnis zum üblichen Grau. */
    static int shade(int color, int brightness) {
        float factor = brightness / (float) BASE_BRIGHTNESS;
        int r = clamp((color >> 16 & 0xFF) * factor);
        int g = clamp((color >> 8 & 0xFF) * factor);
        int b = clamp((color & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    /** Muster auf Vorder- und Rückseite des Körpers (Vorderseite 22..30 x 26..38, Rückseite 36..44 x 26..38). */
    private static void decorate(Canvas canvas, Outfit outfit) {
        int accent = 0xFF000000 | outfit.accent();
        switch (outfit.mark()) {
            case TIE -> {
                rect(canvas, 25, 29, 2, 6, accent);
                rect(canvas, 25, 29, 2, 1, shade(outfit.accent(), 70));
            }
            case BADGE -> {
                rect(canvas, 27, 30, 2, 2, accent);
                rect(canvas, 23, 33, 4, 1, accent);
                rect(canvas, 39, 28, 4, 2, 0xFFE8E8E8);
            }
            case STRIPES -> {
                rect(canvas, 22, 31, 8, 1, accent);
                rect(canvas, 36, 31, 8, 1, accent);
            }
            case VEST -> {
                rect(canvas, 22, 31, 8, 1, 0xFFE8E8E0);
                rect(canvas, 36, 31, 8, 1, 0xFFE8E8E0);
                rect(canvas, 22, 29, 1, 5, accent);
                rect(canvas, 29, 29, 1, 5, accent);
            }
            case PINSTRIPE -> {
                for (int x = 22; x < 30; x += 2) {
                    rect(canvas, x, 29, 1, 8, 0xFF4A4A50);
                }
                rect(canvas, 25, 29, 2, 5, accent);
            }
            case CHECK -> {
                for (int y = 29; y < 37; y++) {
                    for (int x = 22; x < 30; x++) {
                        if ((x + y) % 2 == 0) {
                            canvas.set(x, y, 0xFF000000 | shade(outfit.robe(), 62));
                        }
                    }
                }
                rect(canvas, 25, 29, 2, 8, 0xFF1D1D1D);
            }
            case COLLAR -> {
                rect(canvas, 23, 29, 2, 3, accent);
                rect(canvas, 27, 29, 2, 3, accent);
            }
            case CAMO -> {
                for (int y = 29; y < 37; y++) {
                    for (int x = 22; x < 44; x++) {
                        if (x >= 30 && x < 36) {
                            continue;
                        }
                        if ((x * 5 + y * 3) % 7 < 2) {
                            canvas.set(x, y, 0xFF000000 | outfit.accent());
                        }
                    }
                }
            }
            case EPAULETTES -> {
                rect(canvas, 23, 29, 6, 1, accent);
                rect(canvas, 24, 31, 4, 1, accent);
                rect(canvas, 24, 33, 4, 1, accent);
                rect(canvas, 36, 29, 8, 1, accent);
            }
            case CARD -> {
                rect(canvas, 27, 30, 2, 2, accent);
                rect(canvas, 27, 30, 2, 1, 0xFFC03030);
            }
            case NONE -> {
            }
        }
    }

    /** Hut auf dem Kopf: Oberseite (8..16 x 0..8) und die Kopfseiten ab Zeile 8; die Augen bei Zeile 13 bleiben frei. */
    private static void hat(Canvas canvas, Outfit outfit) {
        if (outfit.hat() == Hat.NONE) {
            return;
        }
        int color = 0xFF000000 | outfit.hatColor();
        int trim = 0xFF000000 | outfit.hatTrim();
        rect(canvas, 8, 0, 8, 8, color);
        int bandRows = switch (outfit.hat()) {
            case TOP_HAT, STRAW -> 3;
            default -> 2;
        };
        for (int[] side : new int[][] {{0, 8}, {8, 8}, {16, 8}, {24, 8}}) {
            rect(canvas, side[0], 8, side[1], bandRows, color);
        }
        switch (outfit.hat()) {
            case CAP, PEAKED -> {
                rect(canvas, 9, 10, 6, 1, trim);
                if (outfit.hat() == Hat.PEAKED) {
                    rect(canvas, 8, 9, 8, 1, 0xFF000000 | outfit.accent());
                    rect(canvas, 0, 9, 8, 1, 0xFF000000 | outfit.accent());
                    rect(canvas, 16, 9, 8, 1, 0xFF000000 | outfit.accent());
                    rect(canvas, 24, 9, 8, 1, 0xFF000000 | outfit.accent());
                }
            }
            case HARD_HAT -> {
                rect(canvas, 11, 0, 2, 8, trim);
                for (int[] side : new int[][] {{0, 8}, {8, 8}, {16, 8}, {24, 8}}) {
                    rect(canvas, side[0], 10, side[1], 1, trim);
                }
                rect(canvas, 11, 8, 2, 2, 0xFF000000 | outfit.accent());
            }
            case FEDORA -> {
                for (int[] side : new int[][] {{0, 8}, {8, 8}, {16, 8}, {24, 8}}) {
                    rect(canvas, side[0], 9, side[1], 1, trim);
                    rect(canvas, side[0], 10, side[1], 1, shade(outfit.hatColor(), 75));
                }
            }
            case TOP_HAT -> {
                for (int[] side : new int[][] {{0, 8}, {8, 8}, {16, 8}, {24, 8}}) {
                    rect(canvas, side[0], 10, side[1], 1, trim);
                }
            }
            case STRAW -> {
                for (int[] side : new int[][] {{0, 8}, {8, 8}, {16, 8}, {24, 8}}) {
                    rect(canvas, side[0], 10, side[1], 1, trim);
                    rect(canvas, side[0], 11, side[1], 1, color);
                }
            }
            case BEANIE -> {
                for (int[] side : new int[][] {{0, 8}, {8, 8}, {16, 8}, {24, 8}}) {
                    rect(canvas, side[0], 9, side[1], 1, trim);
                }
            }
            case NONE -> {
            }
        }
    }

    private static void rect(Canvas canvas, int x, int y, int width, int height, int argb) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                canvas.set(x + dx, y + dy, argb);
            }
        }
    }
}
