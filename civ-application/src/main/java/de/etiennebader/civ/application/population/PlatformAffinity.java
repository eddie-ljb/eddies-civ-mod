package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.population.Personality;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Wie sehr ein Wahlprogramm zu einer Persönlichkeit passt, und welche Stichworte ein Thema verraten.
 */
public final class PlatformAffinity {

    private static final Map<Platform, List<String>> KEYWORDS = new EnumMap<>(Platform.class);

    static {
        KEYWORDS.put(Platform.ORDER, List.of("ordnung", "sicherheit", "schutz", "verbot", "regeln", "polizei", "order",
                "safety", "security"));
        KEYWORDS.put(Platform.FREEDOM, List.of("freiheit", "frei", "erlaub", "selbst entscheiden", "freedom", "liberty",
                "free"));
        KEYWORDS.put(Platform.LOW_TAXES, List.of("steuer", "sparen", "abgabe", "entlast", "tax", "save"));
        KEYWORDS.put(Platform.INVESTMENT, List.of("ausbau", "bauen", "investi", "straße", "build", "invest"));
        KEYWORDS.put(Platform.ENVIRONMENT, List.of("umwelt", "natur", "wald", "tiere", "klima", "grün", "environment",
                "nature", "forest"));
        KEYWORDS.put(Platform.WELFARE, List.of("sozial", "gerecht", "armut", "helfen", "solidar", "rente", "welfare",
                "fair", "poor"));
        KEYWORDS.put(Platform.ECONOMY, List.of("wirtschaft", "handel", "markt", "firma", "börse", "arbeitspl", "economy",
                "trade", "market", "business"));
        KEYWORDS.put(Platform.EDUCATION, List.of("bildung", "schule", "wissen", "lernen", "bibliothek", "education",
                "school", "knowledge"));
        KEYWORDS.put(Platform.TRADITION, List.of("tradition", "brauch", "werte", "glaube", "ahnen", "bewahren",
                "heritage", "values"));
        KEYWORDS.put(Platform.PROGRESS, List.of("fortschritt", "technik", "redstone", "erfind", "modern", "zukunft",
                "progress", "technology", "future"));
        KEYWORDS.put(Platform.PEACE, List.of("frieden", "diplomat", "bündnis", "versöhn", "verhandl", "peace",
                "alliance", "diplomacy"));
        KEYWORDS.put(Platform.EXPANSION, List.of("expansion", "stärke", "gebiet", "erober", "armee", "verteidig",
                "strength", "territory", "defend"));
        KEYWORDS.put(Platform.DEMOCRACY, List.of("demokrat", "beteilig", "transparen", "abstimm", "mitbestimm",
                "pressefreiheit", "democracy", "participation", "transparency"));
        KEYWORDS.put(Platform.AGRICULTURE, List.of("landwirt", "ernte", "felder", "bauern", "vieh", "nahrung", "farm",
                "harvest", "crops"));
        KEYWORDS.put(Platform.AUTONOMY, List.of("heimat", "selbstbestimm", "unabhängig", "eigenständig", "vor ort",
                "autonomy", "independence", "local"));
    }

    private PlatformAffinity() {
    }

    /** Zustimmung von -1 (lehnt ab) bis 1 (überzeugt); {@link Platform#NONE} ist 0. */
    public static double of(Platform platform, Personality personality) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(personality, "personality");
        double value = switch (platform) {
            case NONE -> 0;
            case ORDER -> personality.order();
            case FREEDOM -> -personality.order();
            case LOW_TAXES -> personality.thrift();
            case INVESTMENT -> -personality.thrift();
            case ENVIRONMENT -> personality.nature();
            case WELFARE -> personality.community();
            case ECONOMY -> (personality.thrift() - personality.community()) / 2.0;
            case EDUCATION -> (personality.community() - personality.tradition()) / 2.0;
            case TRADITION -> personality.tradition() * 0.7 + personality.belonging() * 0.3;
            case PROGRESS -> -personality.tradition();
            case PEACE -> personality.peace();
            case EXPANSION -> (personality.order() - personality.peace()) / 2.0;
            case DEMOCRACY -> (personality.community() - personality.order()) / 2.0;
            case AGRICULTURE -> (personality.nature() + personality.tradition()) / 2.0;
            case AUTONOMY -> personality.belonging() * 0.7 - personality.peace() * 0.3;
        };
        return Math.max(-1, Math.min(1, value / Personality.MAX));
    }

    /** Das Programm, das dieser Persönlichkeit am meisten zusagt. */
    public static Platform preferred(Personality personality) {
        Platform best = Platform.ORDER;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Platform platform : Platform.values()) {
            if (platform == Platform.NONE) {
                continue;
            }
            double value = of(platform, personality);
            if (value > bestValue) {
                best = platform;
                bestValue = value;
            }
        }
        return best;
    }

    /** Das Thema eines Textes nach Stichworten; ohne erkennbares Thema {@link Platform#NONE}. */
    public static Platform topicOf(String text) {
        String lower = Objects.requireNonNull(text, "text").toLowerCase(Locale.ROOT);
        Platform best = Platform.NONE;
        int bestCount = 0;
        for (Map.Entry<Platform, List<String>> entry : KEYWORDS.entrySet()) {
            int found = 0;
            for (String word : entry.getValue()) {
                if (lower.contains(word)) {
                    found++;
                }
            }
            if (found > bestCount) {
                best = entry.getKey();
                bestCount = found;
            }
        }
        return best;
    }

    /** Stichworte eines Themas, z. B. für Hilfetexte. */
    public static List<String> keywords(Platform platform) {
        return KEYWORDS.getOrDefault(platform, List.of());
    }
}
