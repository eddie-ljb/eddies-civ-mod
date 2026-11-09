package de.etiennebader.civ.server.market;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Grundpreise aller Items in Hundertsteln eines Smaragds, bei leerem Lager und neutralem Preisniveau.
 *
 * <p>Reihenfolge: (1) nicht handelbare Items, (2) feste Preise wichtiger Rohstoffe,
 * (3) Regeln nach Namensendung und Material (Holzarten, Farben, Werkzeuge, Rüstung),
 * (4) Schätzung aus Seltenheit, Stapelgröße und Blockeigenschaft. So hat jedes Item einen Preis,
 * auch eines aus einem anderen Mod.
 */
public final class BasePrices {

    /** Items, die nur Admins oder der Kreativmodus haben sollen. */
    private static final Set<String> NOT_TRADABLE = Set.of(
            "minecraft:air", "minecraft:bedrock", "minecraft:barrier", "minecraft:light", "minecraft:structure_void",
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:jigsaw",
            "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:spawner", "minecraft:trial_spawner",
            "minecraft:end_portal_frame", "minecraft:reinforced_deepslate", "minecraft:petrified_oak_slab",
            "minecraft:test_block", "minecraft:test_instance_block", "minecraft:vault",
            "eddies-civ-mod:town_hall");

    /** Feste Preise; Schlüssel ohne Namensraum gelten für {@code minecraft:}. */
    private static final Map<String, Long> FIXED = Map.ofEntries(
            // Erde und Gestein
            Map.entry("dirt", 1L), Map.entry("grass_block", 2L), Map.entry("cobblestone", 1L), Map.entry("stone", 2L),
            Map.entry("cobbled_deepslate", 1L), Map.entry("deepslate", 2L), Map.entry("netherrack", 1L),
            Map.entry("sand", 1L), Map.entry("red_sand", 1L), Map.entry("gravel", 1L), Map.entry("clay_ball", 3L),
            Map.entry("granite", 2L), Map.entry("diorite", 2L), Map.entry("andesite", 2L), Map.entry("tuff", 2L),
            Map.entry("calcite", 3L), Map.entry("end_stone", 3L), Map.entry("blackstone", 2L), Map.entry("basalt", 2L),
            Map.entry("soul_sand", 3L), Map.entry("soul_soil", 3L), Map.entry("obsidian", 40L),
            Map.entry("crying_obsidian", 60L), Map.entry("glass", 4L), Map.entry("bricks", 10L), Map.entry("snowball", 1L),
            Map.entry("ice", 4L), Map.entry("packed_ice", 30L), Map.entry("blue_ice", 250L), Map.entry("mud", 2L),
            // Holz
            Map.entry("stick", 1L), Map.entry("bamboo", 1L), Map.entry("charcoal", 12L),
            // Erze und Mineralien
            Map.entry("coal", 15L), Map.entry("raw_iron", 45L), Map.entry("iron_ingot", 60L), Map.entry("iron_nugget", 6L),
            Map.entry("iron_block", 540L), Map.entry("raw_copper", 10L), Map.entry("copper_ingot", 15L),
            Map.entry("copper_block", 135L), Map.entry("raw_gold", 90L), Map.entry("gold_ingot", 120L),
            Map.entry("gold_nugget", 12L), Map.entry("gold_block", 1_080L), Map.entry("redstone", 10L),
            Map.entry("redstone_block", 90L), Map.entry("lapis_lazuli", 20L), Map.entry("lapis_block", 180L),
            Map.entry("diamond", 1_500L), Map.entry("diamond_block", 13_500L), Map.entry("emerald", 100L),
            Map.entry("emerald_block", 900L), Map.entry("quartz", 15L), Map.entry("amethyst_shard", 20L),
            Map.entry("netherite_scrap", 4_000L), Map.entry("netherite_ingot", 18_000L), Map.entry("ancient_debris", 4_000L),
            Map.entry("glowstone_dust", 10L), Map.entry("flint", 3L), Map.entry("prismarine_shard", 15L),
            Map.entry("prismarine_crystals", 20L), Map.entry("echo_shard", 800L),
            // Landwirtschaft und Nahrung
            Map.entry("wheat", 4L), Map.entry("wheat_seeds", 1L), Map.entry("carrot", 3L), Map.entry("potato", 3L),
            Map.entry("beetroot", 3L), Map.entry("melon_slice", 1L), Map.entry("pumpkin", 6L), Map.entry("sugar_cane", 2L),
            Map.entry("sugar", 2L), Map.entry("apple", 8L), Map.entry("bread", 12L), Map.entry("cocoa_beans", 6L),
            Map.entry("egg", 3L), Map.entry("milk_bucket", 70L), Map.entry("honey_bottle", 25L),
            Map.entry("beef", 8L), Map.entry("cooked_beef", 15L), Map.entry("porkchop", 8L),
            Map.entry("cooked_porkchop", 15L), Map.entry("chicken", 6L), Map.entry("cooked_chicken", 12L),
            Map.entry("mutton", 6L), Map.entry("cooked_mutton", 12L), Map.entry("cod", 5L), Map.entry("salmon", 6L),
            Map.entry("golden_apple", 1_200L), Map.entry("enchanted_golden_apple", 30_000L),
            Map.entry("golden_carrot", 120L), Map.entry("cake", 60L), Map.entry("pumpkin_pie", 20L),
            Map.entry("cactus", 3L), Map.entry("kelp", 1L), Map.entry("nether_wart", 10L),
            // Tierprodukte und Mob-Drops
            Map.entry("leather", 20L), Map.entry("string", 5L), Map.entry("feather", 3L), Map.entry("bone", 5L),
            Map.entry("bone_meal", 2L), Map.entry("gunpowder", 15L), Map.entry("rotten_flesh", 1L),
            Map.entry("spider_eye", 5L), Map.entry("ender_pearl", 150L), Map.entry("blaze_rod", 200L),
            Map.entry("slime_ball", 30L), Map.entry("ink_sac", 6L), Map.entry("ghast_tear", 400L),
            Map.entry("phantom_membrane", 150L), Map.entry("rabbit_hide", 8L), Map.entry("scute", 200L),
            Map.entry("armadillo_scute", 150L), Map.entry("breeze_rod", 250L), Map.entry("shulker_shell", 1_000L),
            // Seltenes
            Map.entry("nether_star", 50_000L), Map.entry("totem_of_undying", 5_000L), Map.entry("elytra", 20_000L),
            Map.entry("heart_of_the_sea", 5_000L), Map.entry("trident", 4_000L), Map.entry("dragon_egg", 100_000L),
            Map.entry("saddle", 300L), Map.entry("name_tag", 300L), Map.entry("enchanted_book", 500L),
            // Verarbeitetes
            Map.entry("paper", 3L), Map.entry("book", 30L), Map.entry("bookshelf", 110L), Map.entry("torch", 3L),
            Map.entry("chest", 20L), Map.entry("crafting_table", 10L), Map.entry("furnace", 10L), Map.entry("arrow", 4L),
            Map.entry("bucket", 180L), Map.entry("rail", 25L), Map.entry("powered_rail", 140L), Map.entry("tnt", 80L),
            Map.entry("bell", 2_000L), Map.entry("lantern", 20L), Map.entry("map", 260L), Map.entry("compass", 260L),
            Map.entry("clock", 500L), Map.entry("bow", 20L), Map.entry("shield", 70L), Map.entry("anvil", 1_700L));

    /** Preis nach Namensendung, in fester Prüfreihenfolge: spezifischere Endungen zuerst. */
    private static final List<Map.Entry<String, Long>> SUFFIXES = List.of(
            Map.entry("_concrete_powder", 4L), Map.entry("_glazed_terracotta", 9L), Map.entry("_stained_glass_pane", 3L),
            Map.entry("_stained_glass", 6L), Map.entry("_terracotta", 7L), Map.entry("_concrete", 6L),
            Map.entry("_wool", 8L), Map.entry("_carpet", 5L), Map.entry("_bed", 30L), Map.entry("_banner", 50L),
            Map.entry("_dye", 3L), Map.entry("_log", 8L), Map.entry("_wood", 8L), Map.entry("_stem", 8L),
            Map.entry("_hyphae", 8L), Map.entry("_planks", 2L), Map.entry("_slab", 1L), Map.entry("_stairs", 3L),
            Map.entry("_fence", 3L), Map.entry("_fence_gate", 5L), Map.entry("_door", 4L), Map.entry("_trapdoor", 4L),
            Map.entry("_sign", 4L), Map.entry("_button", 1L), Map.entry("_pressure_plate", 2L), Map.entry("_sapling", 4L),
            Map.entry("_leaves", 1L), Map.entry("_boat", 12L), Map.entry("_ore", 40L), Map.entry("_wall", 2L),
            Map.entry("_bricks", 8L), Map.entry("_shulker_box", 2_100L), Map.entry("_candle", 15L),
            Map.entry("_music_disc", 1_500L), Map.entry("_pottery_sherd", 300L), Map.entry("_smithing_template", 2_000L),
            Map.entry("_horse_armor", 800L), Map.entry("_harness", 300L));

    /** Werkzeuge und Rüstung: Preis je Material, mal Faktor je Teil. */
    private static final Map<String, Long> MATERIALS = Map.of(
            "wooden", 2L, "stone", 3L, "leather", 20L, "chainmail", 60L, "copper", 15L, "iron", 60L, "golden", 120L,
            "diamond", 1_500L, "netherite", 18_000L);
    private static final Map<String, Long> PARTS = Map.ofEntries(
            Map.entry("_sword", 2L), Map.entry("_pickaxe", 3L), Map.entry("_axe", 3L), Map.entry("_shovel", 1L),
            Map.entry("_hoe", 2L), Map.entry("_spear", 2L), Map.entry("_helmet", 5L), Map.entry("_chestplate", 8L),
            Map.entry("_leggings", 7L), Map.entry("_boots", 4L));

    private BasePrices() {
    }

    /**
     * @return Grundpreis eines Stücks; leer, wenn das Item nicht gehandelt werden darf
     */
    public static OptionalLong of(ItemTraits item) {
        if (NOT_TRADABLE.contains(item.id()) || item.path().endsWith("_spawn_egg")) {
            return OptionalLong.empty();
        }
        String path = item.path();
        boolean vanilla = item.id().startsWith("minecraft:");
        if (vanilla && FIXED.containsKey(path)) {
            return OptionalLong.of(FIXED.get(path));
        }
        OptionalLong equipment = equipmentPrice(path);
        if (equipment.isPresent()) {
            return equipment;
        }
        for (Map.Entry<String, Long> suffix : SUFFIXES) {
            if (path.endsWith(suffix.getKey())) {
                return OptionalLong.of(suffix.getValue());
            }
        }
        return OptionalLong.of(estimate(item));
    }

    private static OptionalLong equipmentPrice(String path) {
        for (Map.Entry<String, Long> part : PARTS.entrySet()) {
            if (!path.endsWith(part.getKey())) {
                continue;
            }
            String material = path.substring(0, path.length() - part.getKey().length());
            Long materialPrice = MATERIALS.get(material);
            if (materialPrice != null) {
                return OptionalLong.of(materialPrice * part.getValue());
            }
        }
        return OptionalLong.empty();
    }

    /** Schätzung für Items ohne Regel, z. B. aus anderen Mods. */
    private static long estimate(ItemTraits item) {
        long byRarity = switch (item.rarity()) {
            case COMMON -> item.block() ? 3 : 5;
            case UNCOMMON -> 150;
            case RARE -> 800;
            case EPIC -> 3_000;
        };
        if (item.maxStackSize() == 1) {
            return Math.max(byRarity, 100);
        }
        if (item.maxStackSize() <= 16) {
            return Math.max(byRarity, 20);
        }
        return byRarity;
    }
}
