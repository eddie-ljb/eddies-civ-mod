package de.etiennebader.civ.fabric.content;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Das Handschellen-Item (Issue #84): fesselt oder löst Spieler und Dorfbewohner per Rechtsklick (Polizei). */
public final class HandcuffItems {

    private static Item item;

    private HandcuffItems() {
    }

    static void init(Item handcuffs) {
        item = Objects.requireNonNull(handcuffs, "handcuffs");
    }

    public static Item item() {
        return Objects.requireNonNull(item, "Handschellen sind noch nicht registriert");
    }

    public static boolean isHandcuffs(ItemStack stack) {
        return item != null && stack.is(item);
    }
}
