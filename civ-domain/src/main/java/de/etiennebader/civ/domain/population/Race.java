package de.etiennebader.civ.domain.population;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rasse einer Gemeinde. Sie bestimmt Körpergröße und, mit dem Mod auf dem Client, Hautton und Haarfarbe der
 * Dorfbewohner. Die Größe sehen auch Spieler ohne Mod.
 */
public enum Race {
    HUMAN("mensch", 1.0F),
    DWARF("zwerg", 0.8F),
    ELF("elf", 1.15F),
    ORC("ork", 1.25F);

    public static final Race DEFAULT = HUMAN;

    private final String word;
    private final float scale;

    Race(String word, float scale) {
        this.word = word;
        this.scale = scale;
    }

    public String word() {
        return word;
    }

    /** Größenfaktor des Dorfbewohners (1,0 = Vanilla). */
    public float scale() {
        return scale;
    }

    public static List<String> words() {
        return Arrays.stream(values()).map(Race::word).toList();
    }

    /** Rasse aus deutschem oder englischem Wort ({@code zwerg}, {@code dwarf}); leer, wenn unbekannt. */
    public static Optional<Race> parse(String input) {
        String text = input.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(race -> race.word.equals(text)
                || race.name().toLowerCase(Locale.ROOT).equals(text)).findFirst();
    }

    /**
     * Die Rasse zu einem gemessenen Größenfaktor, damit der Client sie ohne eigene Übertragung erkennt; Babys und
     * unbekannte Größen gelten als Mensch.
     */
    public static Race fromScale(float measured) {
        for (Race race : values()) {
            if (Math.abs(race.scale - measured) < 0.03F) {
                return race;
            }
        }
        return HUMAN;
    }
}
