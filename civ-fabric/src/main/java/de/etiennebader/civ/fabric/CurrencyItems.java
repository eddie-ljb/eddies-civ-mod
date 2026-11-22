package de.etiennebader.civ.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/**
 * Bargeld eigener Währungen (Issue #4): Items, die die Bank gegen Abbuchung ausgibt. Sie sehen aus wie das gewählte
 * Item der Währung, tragen aber den Währungscode als Kennzeichnung und den Währungsnamen als Bezeichnung. Nur so
 * gekennzeichnete Items nimmt die Bank wieder an; ein gewöhnliches Item gleicher Art ist kein Geld, damit sich
 * niemand durch eine Währung mit hohem Kurs und billigem Item Geld drucken kann.
 */
public final class CurrencyItems {

    private static final String CURRENCY_KEY = "civ_currency";

    private CurrencyItems() {
    }

    /** Das Item zur ID, falls es in der Registry existiert. */
    public static Optional<Item> item(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
    }

    /** Ob es ein Item mit dieser ID gibt. */
    public static boolean exists(String itemId) {
        return item(itemId).isPresent();
    }

    private static ItemStack stack(Item item, String code, String name, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(CURRENCY_KEY, code);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    private static boolean isMoney(ItemStack stack, Item item, String code) {
        if (!stack.is(item)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getString(CURRENCY_KEY).filter(code::equals).isPresent();
    }

    public static int count(ServerPlayer player, String itemId, String code) {
        Optional<Item> item = item(itemId);
        int count = 0;
        if (item.isPresent()) {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (isMoney(stack, item.get(), code)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /** Nimmt bis zu {@code wanted} gekennzeichnete Geld-Items ab; liefert, wie viele es waren. */
    public static int take(ServerPlayer player, String itemId, String code, int wanted) {
        Optional<Item> item = item(itemId);
        if (item.isEmpty()) {
            return 0;
        }
        int remaining = wanted;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (remaining <= 0) {
                break;
            }
            if (isMoney(stack, item.get(), code)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        player.containerMenu.broadcastChanges();
        return wanted - remaining;
    }

    /** Gibt gekennzeichnete Geld-Items aus; was nicht passt, fällt auf den Boden. */
    public static void give(ServerPlayer player, String itemId, String code, String name, int amount) {
        Optional<Item> item = item(itemId);
        if (item.isEmpty()) {
            return;
        }
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(remaining, item.get().getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(stack(item.get(), code, name, size), Prediction.SERVER_ONLY);
            remaining -= size;
        }
        player.containerMenu.broadcastChanges();
    }
}
