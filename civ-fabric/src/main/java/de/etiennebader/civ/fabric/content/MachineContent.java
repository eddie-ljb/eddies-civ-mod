package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.domain.production.MachineType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Die Blöcke und Items aller Maschinentypen: je Typ ein eigener Block mit eigener Textur. */
public final class MachineContent {

    private final Map<MachineType, Block> blocks = new EnumMap<>(MachineType.class);
    private final Map<MachineType, Item> items = new EnumMap<>(MachineType.class);

    private MachineContent() {
    }

    public static MachineContent register(MachineBlock.PlacementHandler handler) {
        MachineContent content = new MachineContent();
        for (MachineType type : MachineType.values()) {
            Identifier id = EddiesCivMod.id(type.blockId());
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
            Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new MachineBlock(
                    BlockBehaviour.Properties.of().setId(blockKey).mapColor(MapColor.METAL).strength(3.5F, 6.0F)
                            .sound(SoundType.METAL).requiresCorrectToolForDrops(), type, handler));
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
            Item item = Registry.register(BuiltInRegistries.ITEM, itemKey,
                    new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
            content.blocks.put(type, block);
            content.items.put(type, item);
        }
        return content;
    }

    public List<Item> items() {
        return List.copyOf(items.values());
    }

    public Item item(MachineType type) {
        return items.get(type);
    }
}
