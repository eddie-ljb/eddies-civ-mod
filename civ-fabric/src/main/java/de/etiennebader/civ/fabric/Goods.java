package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.server.market.ItemTraits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Übersetzt zwischen Minecraft-Items und Waren des Markts.
 */
public final class Goods {

    private Goods() {
    }

    /** Die Eigenschaften eines Stapels, nach denen der Markt Preise festlegt. */
    public static ItemTraits traits(ItemStack stack) {
        Item item = stack.getItem();
        ItemTraits.Rarity rarity = switch (stack.getRarity()) {
            case COMMON -> ItemTraits.Rarity.COMMON;
            case UNCOMMON -> ItemTraits.Rarity.UNCOMMON;
            case RARE -> ItemTraits.Rarity.RARE;
            case EPIC -> ItemTraits.Rarity.EPIC;
        };
        return new ItemTraits(BuiltInRegistries.ITEM.getKey(item).toString(), item.getDescriptionId(), rarity,
                stack.getMaxStackSize(), item instanceof BlockItem, !stack.getComponentsPatch().isEmpty());
    }

    /** Das Item zu einer Ware; leer, wenn es (z. B. nach Entfernen einer Mod) nicht mehr existiert. */
    public static Optional<Item> item(Commodity commodity) {
        return item(commodity.id());
    }

    public static Optional<Item> item(String id) {
        Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (identifier == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(identifier);
    }
}
