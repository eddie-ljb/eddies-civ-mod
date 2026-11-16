package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.server.command.GuiKeys;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Abschriften von Verfassungen: ein Item, das die Gemeinde bzw. den Bund kennt, dessen Verfassung es zeigt.
 */
public final class ConstitutionCopies {

    private static final String POLITY_KEY = "eddies_civ_polity";
    private static Item item;

    private ConstitutionCopies() {
    }

    static void init(Item copy) {
        item = Objects.requireNonNull(copy, "copy");
    }

    /** Eine Abschrift der Verfassung dieser Gemeinde oder dieses Bundes. */
    public static ItemStack create(PolityId polity, String name) {
        ItemStack stack = new ItemStack(Objects.requireNonNull(item, "Abschrift ist noch nicht registriert"));
        CompoundTag tag = new CompoundTag();
        tag.putString(POLITY_KEY, polity.value().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Components.of(GuiKeys.CONSTITUTION_COPY_NAME.text(name))
                .withStyle(style -> style.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.<Component>of(
                Components.of(GuiKeys.CONSTITUTION_COPY_LORE.text()))));
        return stack;
    }

    public static boolean isCopy(ItemStack stack) {
        return item != null && stack.is(item);
    }

    /** Die Polity, deren Verfassung die Abschrift zeigt. */
    public static Optional<PolityId> polityOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        try {
            return data.copyTag().getString(POLITY_KEY).map(UUID::fromString).map(PolityId::new);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
