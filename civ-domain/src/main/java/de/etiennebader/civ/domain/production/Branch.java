package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.market.Commodity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Branche einer Firma: was ein Beschäftigter an einem Tag erzeugt, bei Grundausstattung.
 */
public enum Branch {

    /** Holzfäller: Stämme und Setzlinge. */
    FORESTRY("forst", List.of(new Yield("minecraft:oak_log", 24), new Yield("minecraft:spruce_log", 8),
            new Yield("minecraft:oak_sapling", 2))),
    /** Schäferei: Wolle und Hammelfleisch. */
    SHEEP("schaeferei", List.of(new Yield("minecraft:white_wool", 20), new Yield("minecraft:mutton", 6))),
    /** Ackerbau: Weizen, Karotten, Kartoffeln. */
    FARM("acker", List.of(new Yield("minecraft:wheat", 40), new Yield("minecraft:carrot", 16),
            new Yield("minecraft:potato", 16))),
    /** Bergwerk: Bruchstein, Kohle, Eisenerz. */
    MINE("bergwerk", List.of(new Yield("minecraft:cobblestone", 48), new Yield("minecraft:coal", 6),
            new Yield("minecraft:raw_iron", 2))),
    /** Fischerei: Kabeljau, Lachs. */
    FISHERY("fischerei", List.of(new Yield("minecraft:cod", 24), new Yield("minecraft:salmon", 12))),
    /** Viehzucht: Rind, Leder, Eier. */
    RANCH("viehzucht", List.of(new Yield("minecraft:beef", 12), new Yield("minecraft:leather", 4),
            new Yield("minecraft:egg", 12))),
    /** Ziegelei: Ziegel und Glas. */
    BRICKWORKS("ziegelei", List.of(new Yield("minecraft:bricks", 12), new Yield("minecraft:glass", 16))),
    /** Imkerei und Obstbau: Äpfel, Honig. */
    ORCHARD("obstbau", List.of(new Yield("minecraft:apple", 12), new Yield("minecraft:honey_bottle", 2)));

    /** Menge einer Ware je Beschäftigtem und Tag. */
    public record Yield(String commodity, int perWorker) {

        public Commodity asCommodity() {
            return Commodity.of(commodity);
        }
    }

    private final String id;
    private final List<Yield> yields;

    Branch(String id, List<Yield> yields) {
        this.id = id;
        this.yields = List.copyOf(yields);
    }

    /** Kennung für Befehle, z. B. {@code forst}. */
    public String id() {
        return id;
    }

    public List<Yield> yields() {
        return yields;
    }

    public static Optional<Branch> of(String text) {
        String key = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(branch -> branch.id.equals(key) || branch.name().equalsIgnoreCase(key)
                        || alias(branch).equals(key))
                .findFirst();
    }

    /** Englischer Name für Befehle, z. B. {@code forestry}. */
    public static String alias(Branch branch) {
        return branch.name().toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(Branch::id).toList();
    }
}
