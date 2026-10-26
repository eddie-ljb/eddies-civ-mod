package de.etiennebader.civ.sim.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Erzeugt deutsch klingende Ortsnamen wie „Eichenfurt“. Wiederholt sich ein Name, bekommt er eine Nummer.
 */
public final class PolityNames {

    private static final List<String> PREFIXES = List.of(
            "Nord", "Süd", "Ost", "West", "Eichen", "Birken", "Linden", "Stein", "Wald", "Berg", "See", "Fluss",
            "Rosen", "Mühl", "Kirch", "Hohen", "Tannen", "Wiesen");
    private static final List<String> SUFFIXES = List.of(
            "hain", "furt", "heim", "dorf", "burg", "feld", "bach", "au", "tal", "hausen", "brück", "rode");

    private final RandomGenerator random;
    private final Map<String, Integer> usage = new HashMap<>();

    public PolityNames(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        String base = PREFIXES.get(random.nextInt(PREFIXES.size())) + SUFFIXES.get(random.nextInt(SUFFIXES.size()));
        int count = usage.merge(base, 1, Integer::sum);
        return count == 1 ? base : base + " " + count;
    }
}
