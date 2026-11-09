package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.EddiesCivMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Blöcke und Items des Mods. Wird einmal beim Start registriert.
 */
public final class CivContent {

    public static final Identifier TOWN_HALL_ID = EddiesCivMod.id("town_hall");
    public static final Identifier BANK_ID = EddiesCivMod.id("bank");
    public static final Identifier MARKET_ID = EddiesCivMod.id("market");
    public static final Identifier PLOT_WAND_ID = EddiesCivMod.id("plot_wand");
    public static final Identifier TAB_ID = EddiesCivMod.id("civ");

    /** Wie Grundgestein: im Überlebensmodus nicht abbaubar, gegen Explosionen fest. */
    private static final float UNBREAKABLE = -1.0F;
    private static final float BLAST_PROOF = 3_600_000.0F;

    private final Block townHall;
    private final Item townHallItem;
    private final Block bank;
    private final Block market;
    private final Item plotWand;

    private CivContent(Block townHall, Item townHallItem, Block bank, Block market, Item plotWand) {
        this.townHall = townHall;
        this.townHallItem = townHallItem;
        this.bank = bank;
        this.market = market;
        this.plotWand = plotWand;
    }

    public static CivContent register(TownHallBlock.PlacementHandler townHallPlacement) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TOWN_HALL_ID);
        Block townHall = Registry.register(BuiltInRegistries.BLOCK, blockKey, new TownHallBlock(
                BlockBehaviour.Properties.of()
                        .setId(blockKey)
                        .mapColor(MapColor.STONE)
                        .strength(UNBREAKABLE, BLAST_PROOF)
                        .sound(SoundType.STONE)
                        .noLootTable(),
                townHallPlacement));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TOWN_HALL_ID);
        Item townHallItem = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(townHall, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

        Block bank = simpleBlock(BANK_ID, MapColor.METAL, SoundType.METAL, 5.0F);
        Block market = simpleBlock(MARKET_ID, MapColor.WOOD, SoundType.WOOD, 2.5F);
        Item bankItem = blockItem(BANK_ID, bank);
        Item marketItem = blockItem(MARKET_ID, market);

        ResourceKey<Item> wandKey = ResourceKey.create(Registries.ITEM, PLOT_WAND_ID);
        Item plotWand = Registry.register(BuiltInRegistries.ITEM, wandKey,
                new Item(new Item.Properties().setId(wandKey).stacksTo(1).rarity(Rarity.UNCOMMON)));

        registerTab(townHallItem, bankItem, marketItem, plotWand);
        return new CivContent(townHall, townHallItem, bank, market, plotWand);
    }

    /**
     * Eigener Reiter im Kreativinventar: zuerst die Blöcke und Werkzeuge des Mods, danach Vanilla-Items,
     * die man zum Ausprobieren braucht (Geld, Ladentruhe, Dorfbewohner, Dinge für den Gebietsschutz).
     */
    private static void registerTab(Item townHall, Item bank, Item market, Item plotWand) {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TAB_ID);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + EddiesCivMod.MOD_ID + ".civ"))
                .icon(() -> new ItemStack(townHall))
                .displayItems((parameters, output) -> {
                    output.accept(townHall);
                    output.accept(bank);
                    output.accept(market);
                    output.accept(plotWand);
                    output.accept(Items.EMERALD);
                    output.accept(Items.EMERALD_BLOCK);
                    output.accept(Items.CHEST);
                    output.accept(Items.VILLAGER_SPAWN_EGG);
                    output.accept(Items.CREEPER_SPAWN_EGG);
                    output.accept(Items.ENDERMAN_SPAWN_EGG);
                    output.accept(Items.TNT);
                    output.accept(Items.FLINT_AND_STEEL);
                    output.accept(Items.LAVA_BUCKET);
                    output.accept(Items.PISTON);
                    output.accept(Items.HOPPER);
                })
                .build());
    }

    /** Ein Block ohne eigenes Verhalten; Rechtsklicks fängt {@code ProtectionListener} ab und öffnet das Menü. */
    private static Block simpleBlock(Identifier id, MapColor color, SoundType sound, float hardness) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(color)
                .strength(hardness, 6.0F)
                .sound(sound)));
    }

    private static Item blockItem(Identifier id, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    public Block townHall() {
        return townHall;
    }

    public Item townHallItem() {
        return townHallItem;
    }

    public Block bank() {
        return bank;
    }

    public Block market() {
        return market;
    }

    /** Werkzeug für Grundstücke: Linksklick setzt Ecke 1, Rechtsklick Ecke 2. */
    public Item plotWand() {
        return plotWand;
    }
}
