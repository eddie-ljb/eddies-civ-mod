package de.etiennebader.civ.fabric.gui;

import de.etiennebader.civ.EddiesCivMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Ein Truhenmenü, das nur auf dem Server lebt: Der Client sieht eine gewöhnliche Truhe, deshalb
 * funktioniert es auch ohne den Mod auf dem Client. Klicks werden abgefangen und an den {@link Screen}
 * weitergegeben; Gegenstände lassen sich weder herausnehmen noch hineinlegen.
 */
final class CivMenu extends ChestMenu {

    private static final int OUTSIDE = -999;

    private final Screen screen;
    private final Canvas canvas;
    private final ServerPlayer viewer;

    private CivMenu(int containerId, Inventory inventory, ServerPlayer viewer, Screen screen, SimpleContainer container) {
        super(typeFor(screen.rows()), containerId, inventory, container, screen.rows());
        this.screen = screen;
        this.viewer = viewer;
        this.canvas = new Canvas(container);
        redraw();
    }

    static CivMenu create(int containerId, Inventory inventory, ServerPlayer viewer, Screen screen) {
        return new CivMenu(containerId, inventory, viewer, screen, new SimpleContainer(screen.rows() * 9));
    }

    private void redraw() {
        canvas.clear();
        screen.render(canvas);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId == OUTSIDE || slotId < 0 || slotId >= slots.size()
                || (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE)) {
            sendAllDataToRemote();
            return;
        }
        Click click = new Click(viewer, button == 1, input == ContainerInput.QUICK_MOVE);
        Screens.handlingClick(true);
        try {
            if (slotId < canvas.size()) {
                canvas.action(slotId).accept(click);
            } else {
                screen.clickedInventory(click, slots.get(slotId));
            }
        } catch (RuntimeException e) {
            EddiesCivMod.LOGGER.error("Fehler in einem Menü des Mods", e);
        } finally {
            Screens.handlingClick(false);
        }
        redraw();
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static MenuType<ChestMenu> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Ein Menü hat 1 bis 6 Zeilen: " + rows);
        };
    }
}
