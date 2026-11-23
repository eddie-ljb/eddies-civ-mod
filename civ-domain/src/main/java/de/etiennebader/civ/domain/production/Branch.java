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
    ORCHARD("obstbau", List.of(new Yield("minecraft:apple", 12), new Yield("minecraft:honey_bottle", 2))),
    /**
     * Baufirma: Baumaterial (Steinziegel, Bretter, Glas). Die Beschäftigten sind die Bauarbeiter; Bauherren
     * beauftragen die Firma und beliefern sie oder bezahlen sie (Issue #19).
     */
    CONSTRUCTION("bau", List.of(new Yield("minecraft:stone_bricks", 24), new Yield("minecraft:oak_planks", 24),
            new Yield("minecraft:glass", 8))),
    /**
     * Wartungsfirma: wartet die Maschinen anderer Firmen gegen Geld (Issue Firmengrundstücke und Maschinen) und
     * stellt nebenbei Metallteile her.
     */
    MAINTENANCE("wartung", List.of(new Yield("minecraft:iron_ingot", 6), new Yield("minecraft:copper_ingot", 4)));

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

    /**
     * Waren, die die Firma über die Tagesproduktion hinaus verkaufen darf, weil sie thematisch zur Branche passen
     * (z. B. Kakteen bei der Landwirtschaft). Sie werden eingelagert und wie das übrige Lager verkauft.
     */
    public List<String> extras() {
        return switch (this) {
            case FORESTRY -> List.of("minecraft:birch_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
                    "minecraft:jungle_log", "minecraft:stick", "minecraft:birch_sapling", "minecraft:spruce_sapling");
            case SHEEP -> List.of("minecraft:black_wool", "minecraft:gray_wool", "minecraft:brown_wool",
                    "minecraft:red_wool", "minecraft:blue_wool", "minecraft:string");
            case FARM -> List.of("minecraft:cactus", "minecraft:sugar_cane", "minecraft:pumpkin",
                    "minecraft:melon_slice", "minecraft:beetroot", "minecraft:sweet_berries", "minecraft:bamboo",
                    "minecraft:hay_block");
            case MINE -> List.of("minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                    "minecraft:gravel", "minecraft:flint", "minecraft:raw_copper", "minecraft:redstone");
            case FISHERY -> List.of("minecraft:tropical_fish", "minecraft:pufferfish", "minecraft:kelp",
                    "minecraft:ink_sac", "minecraft:sea_pickle", "minecraft:lily_pad");
            case RANCH -> List.of("minecraft:porkchop", "minecraft:chicken", "minecraft:feather", "minecraft:rabbit",
                    "minecraft:bone", "minecraft:hay_block");
            case BRICKWORKS -> List.of("minecraft:clay_ball", "minecraft:brick", "minecraft:terracotta",
                    "minecraft:flower_pot", "minecraft:sand");
            case ORCHARD -> List.of("minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:honeycomb",
                    "minecraft:cocoa_beans", "minecraft:sugar", "minecraft:melon_slice");
            case CONSTRUCTION -> List.of("minecraft:stone", "minecraft:bricks", "minecraft:birch_planks",
                    "minecraft:spruce_planks", "minecraft:sandstone", "minecraft:iron_bars", "minecraft:ladder");
            case MAINTENANCE -> List.of("minecraft:iron_nugget", "minecraft:redstone", "minecraft:gold_nugget",
                    "minecraft:lever", "minecraft:copper_block");
        };
    }

    /** Ob die Firma diese Ware verkaufen darf: Eigenerzeugnis oder thematisch passende Zusatzware. */
    public boolean accepts(Commodity commodity) {
        return yields.stream().anyMatch(yield -> yield.commodity().equals(commodity.id()))
                || extras().contains(commodity.id());
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
