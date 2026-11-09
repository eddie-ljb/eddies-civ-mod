package de.etiennebader.civ.fabric.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/**
 * Inhalt eines Menüs: zeichnet Symbole und reagiert auf Klicks. Nach jedem Klick wird neu gezeichnet,
 * damit Anzeigen wie Kontostände aktuell bleiben.
 */
public abstract class Screen {

    /** Zeilen der Truhe, 1 bis 6. */
    public abstract int rows();

    public abstract Component title();

    public abstract void render(Canvas canvas);

    /**
     * Klick auf einen Platz im eigenen Inventar des Spielers. Standardmäßig gesperrt, damit nichts zwischen
     * Menü und Inventar verschoben wird.
     *
     * @return ob sich dadurch etwas geändert hat
     */
    public boolean clickedInventory(Click click, Slot slot) {
        return false;
    }
}
