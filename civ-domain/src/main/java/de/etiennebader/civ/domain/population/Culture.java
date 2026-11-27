package de.etiennebader.civ.domain.population;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Kultur einer Gemeinde. Sie bestimmt die Kleidung ihrer Dorfbewohner: Jede Kultur entspricht einem der sieben
 * Dorfbewohner-Stile des Spiels (Ebene, Wüste, Dschungel, Savanne, Schnee, Sumpf, Taiga), die auch Spieler ohne den Mod
 * sehen.
 */
public enum Culture {
    PLAINS("ebene"),
    DESERT("wueste"),
    JUNGLE("dschungel"),
    SAVANNA("savanne"),
    SNOW("schnee"),
    SWAMP("sumpf"),
    TAIGA("taiga");

    public static final Culture DEFAULT = PLAINS;

    private final String word;

    Culture(String word) {
        this.word = word;
    }

    /** Das deutsche Befehlswort, ohne Umlaute. */
    public String word() {
        return word;
    }

    /** Alle Befehlswörter für Vorschläge. */
    public static List<String> words() {
        return Arrays.stream(values()).map(Culture::word).toList();
    }

    /** Kultur aus deutschem oder englischem Wort ({@code wueste}, {@code desert}); leer, wenn unbekannt. */
    public static Optional<Culture> parse(String input) {
        String text = input.strip().toLowerCase(Locale.ROOT).replace("ü", "ue");
        return Arrays.stream(values()).filter(culture -> culture.word.equals(text)
                || culture.name().toLowerCase(Locale.ROOT).equals(text)).findFirst();
    }
}
