package de.etiennebader.civ.fabric.gui;

import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.server.command.Message;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Baut die Gegenstände, die in Menüs als Knöpfe und Anzeigen dienen.
 */
public final class Icon {

    public static final int TITLE = 0xF2C14E;
    public static final int GOOD = 0x7BD88F;
    public static final int BAD = 0xFF6B5E;
    public static final int INFO = 0xB8C4D6;
    public static final int HINT = 0x8A8F98;

    private final ItemStack stack;
    private final List<Component> lore = new ArrayList<>();

    private Icon(ItemStack stack) {
        this.stack = stack;
    }

    public static Icon of(ItemLike item) {
        return new Icon(new ItemStack(item));
    }

    public static Icon of(ItemStack stack) {
        return new Icon(stack.copy());
    }

    public Icon name(Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(style -> style.withItalic(false)));
        return this;
    }

    public Icon name(Message name, int color) {
        return name(Components.plain(name, color));
    }

    public Icon name(Message name) {
        return name(name, TITLE);
    }

    public Icon line(Message line, int color) {
        lore.add(Components.plain(line, color));
        return this;
    }

    public Icon line(Message line) {
        return line(line, INFO);
    }

    public Icon hint(Message line) {
        return line(line, HINT);
    }

    public Icon line(Component line) {
        lore.add(line.copy().withStyle(style -> style.withItalic(false)));
        return this;
    }

    public Icon blank() {
        lore.add(Component.empty());
        return this;
    }

    public Icon count(int count) {
        stack.setCount(Math.max(1, Math.min(count, stack.getMaxStackSize())));
        return this;
    }

    public Icon glint() {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return this;
    }

    /** Kein Tooltip, z. B. für Füllglas. */
    public Icon hidden() {
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
        }
        return stack;
    }

    /** Ein grauer Hinweistext als Lore-Zeile ohne Übersetzung, z. B. für Spielernamen. */
    public static Component literal(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
    }
}
