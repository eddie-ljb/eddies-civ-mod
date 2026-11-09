package de.etiennebader.civ.fabric.gui;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Die Plätze eines Menüs, in die ein {@link Screen} Symbole mit Klick-Aktionen setzt.
 */
public final class Canvas {

    private static final Consumer<Click> NOTHING = click -> {
    };

    private final Container container;
    private final List<Consumer<Click>> actions;

    Canvas(Container container) {
        this.container = container;
        this.actions = new ArrayList<>(Collections.nCopies(container.getContainerSize(), NOTHING));
    }

    public int size() {
        return actions.size();
    }

    /** Ein Symbol ohne Aktion. */
    public void set(int slot, ItemStack icon) {
        set(slot, icon, NOTHING);
    }

    public void set(int slot, ItemStack icon, Consumer<Click> action) {
        container.setItem(slot, Objects.requireNonNull(icon, "icon"));
        actions.set(slot, Objects.requireNonNull(action, "action"));
    }

    /** Spalte und Zeile ab 0. */
    public void set(int column, int row, ItemStack icon, Consumer<Click> action) {
        set(row * 9 + column, icon, action);
    }

    /** Leert alles und füllt mit unbeschrifteten Glasscheiben. */
    void clear() {
        for (int slot = 0; slot < actions.size(); slot++) {
            container.setItem(slot, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
            actions.set(slot, NOTHING);
        }
    }

    /** Färbt eine ganze Zeile, z. B. als Rahmen für Steuerknöpfe. */
    public void fillRow(int row, ItemStack filler) {
        for (int column = 0; column < 9; column++) {
            set(column, row, filler.copy(), NOTHING);
        }
    }

    Consumer<Click> action(int slot) {
        return actions.get(slot);
    }
}
