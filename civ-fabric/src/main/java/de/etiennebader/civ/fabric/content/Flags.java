package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.domain.civic.Flag;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Übersetzt zwischen Bannern im Spiel und {@link Flag}s: Grundfarbe und Muster-Schichten.
 */
public final class Flags {

    private Flags() {
    }

    /** Die Flagge eines Banners in der Hand; leer, wenn es kein Banner ist. */
    public static Optional<Flag> of(ItemStack stack) {
        if (!(stack.getItem() instanceof BannerItem banner)) {
            return Optional.empty();
        }
        BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        List<Flag.Layer> layers = new ArrayList<>();
        for (BannerPatternLayers.Layer layer : patterns.layers()) {
            if (layers.size() >= Flag.MAX_LAYERS) {
                break;
            }
            layers.add(new Flag.Layer(layer.pattern().getRegisteredName(), layer.color().getSerializedName()));
        }
        return Optional.of(new Flag(banner.getColor().getSerializedName(), layers));
    }

    /** Ein Banner mit dieser Flagge; unbekannte Muster fallen weg. */
    public static ItemStack banner(Flag flag, HolderLookup.Provider registries) {
        DyeColor base = DyeColor.byName(flag.base(), DyeColor.WHITE);
        Item item = Items.BANNER.asList().stream()
                .filter(candidate -> candidate instanceof BannerItem banner && banner.getColor() == base)
                .findFirst().orElse(Items.BANNER.white());
        ItemStack stack = new ItemStack(item);
        HolderLookup.RegistryLookup<BannerPattern> patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
        List<BannerPatternLayers.Layer> layers = new ArrayList<>();
        for (Flag.Layer layer : flag.layers()) {
            Identifier id = Identifier.tryParse(layer.pattern());
            if (id == null) {
                continue;
            }
            Optional<Holder.Reference<BannerPattern>> pattern = patterns.get(
                    ResourceKey.create(Registries.BANNER_PATTERN, id));
            pattern.ifPresent(found -> layers.add(new BannerPatternLayers.Layer(found,
                    DyeColor.byName(layer.color(), DyeColor.BLACK))));
        }
        stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
        return stack;
    }
}
