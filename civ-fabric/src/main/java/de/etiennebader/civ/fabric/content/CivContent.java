package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.EddiesCivMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Blöcke und Items des Mods. Wird einmal beim Start registriert.
 */
public final class CivContent {

    public static final Identifier TOWN_HALL_ID = EddiesCivMod.id("town_hall");

    /** Wie Grundgestein: im Überlebensmodus nicht abbaubar, gegen Explosionen fest. */
    private static final float UNBREAKABLE = -1.0F;
    private static final float BLAST_PROOF = 3_600_000.0F;

    private final Block townHall;
    private final Item townHallItem;

    private CivContent(Block townHall, Item townHallItem) {
        this.townHall = townHall;
        this.townHallItem = townHallItem;
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

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> output.accept(townHallItem));
        return new CivContent(townHall, townHallItem);
    }

    public Block townHall() {
        return townHall;
    }

    public Item townHallItem() {
        return townHallItem;
    }
}
