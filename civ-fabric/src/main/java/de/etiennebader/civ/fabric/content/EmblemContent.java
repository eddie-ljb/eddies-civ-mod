package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.EddiesCivMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Die Blöcke und Items für Flagge, Wappen und Wahlplakat (Issue #82). Das Item trägt, was darauf zu sehen ist
 * (leer, einfarbig oder ein hochgeladenes Bild); siehe {@code Emblems}.
 */
public record EmblemContent(Block flag, Item flagItem, Block coatOfArms, Item coatOfArmsItem, Block poster,
                            Item posterItem, BlockEntityType<EmblemBlockEntity> entityType) {

    public static final Identifier FLAG_ID = EddiesCivMod.id("flag");
    public static final Identifier COAT_OF_ARMS_ID = EddiesCivMod.id("coat_of_arms");
    public static final Identifier ENTITY_ID = EddiesCivMod.id("emblem");
    public static final Identifier POSTER_ID = EddiesCivMod.id("campaign_poster");

    private static final VoxelShape POLE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape PEDESTAL = Block.box(3, 0, 0, 13, 16, 5);
    private static final VoxelShape POSTER_STAND = Block.box(3, 0, 5, 13, 16, 11);

    static EmblemContent register() {
        Block flag = block(FLAG_ID, EmblemBlock.Kind.FLAG, POLE, MapColor.WOOD, SoundType.WOOD);
        Block arms = block(COAT_OF_ARMS_ID, EmblemBlock.Kind.COAT_OF_ARMS, PEDESTAL, MapColor.STONE, SoundType.STONE);
        Block poster = block(POSTER_ID, EmblemBlock.Kind.POSTER, POSTER_STAND, MapColor.COLOR_GREEN, SoundType.WOOD);
        ResourceKey<BlockEntityType<?>> typeKey = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, ENTITY_ID);
        @SuppressWarnings("unchecked")
        BlockEntityType<EmblemBlockEntity>[] holder = new BlockEntityType[1];
        holder[0] = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, typeKey,
                new BlockEntityType<>((pos, state) -> new EmblemBlockEntity(holder[0], pos, state),
                        java.util.Set.of(flag, arms, poster)));
        BlockEntityType<EmblemBlockEntity> type = holder[0];
        EmblemBlock.entityType(type);
        return new EmblemContent(flag, item(FLAG_ID, flag), arms, item(COAT_OF_ARMS_ID, arms), poster,
                item(POSTER_ID, poster), type);
    }

    /** Die Block-Items in der Reihenfolge des Kreativ-Reiters. */
    public List<Item> items() {
        return List.of(flagItem, coatOfArmsItem, posterItem);
    }

    /** Ob dieser Block eine Flagge, ein Wappen oder ein Wahlplakat (mit Anzeige) ist. */
    public boolean isEmblem(Block block) {
        return block == flag || block == coatOfArms || block == poster;
    }

    private static Block block(Identifier id, EmblemBlock.Kind kind, VoxelShape shape, MapColor color, SoundType sound) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, key, new EmblemBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(color).strength(1.0F, 3.0F).sound(sound).noOcclusion().noLootTable(),
                kind, shape == null ? Shapes.block() : shape));
    }

    private static Item item(Identifier id, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }
}
