package de.etiennebader.civ.fabric.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Öffnet und schließt Menüs des Mods.
 *
 * <p>Wird aus einem Klick heraus ein anderes Menü geöffnet oder das Menü geschlossen, geschieht das erst
 * am Ende des Serverticks: Minecraft verarbeitet den Klick nach unserem Aufruf noch weiter und erwartet
 * dabei, dass das Menü noch dasselbe ist.
 */
public final class Screens {

    private static final Queue<Runnable> PENDING = new ArrayDeque<>();
    private static boolean handlingClick;

    private Screens() {
    }

    public static void open(ServerPlayer player, Screen screen) {
        run(() -> player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> CivMenu.create(containerId, inventory, player, screen),
                screen.title())));
    }

    public static void close(ServerPlayer player) {
        run(player::closeContainer);
    }

    /** Am Ende jedes Serverticks aufrufen. */
    public static void runPending() {
        Runnable next;
        while ((next = PENDING.poll()) != null) {
            next.run();
        }
    }

    static void handlingClick(boolean active) {
        handlingClick = active;
    }

    private static void run(Runnable action) {
        if (handlingClick) {
            PENDING.add(action);
        } else {
            action.run();
        }
    }
}
