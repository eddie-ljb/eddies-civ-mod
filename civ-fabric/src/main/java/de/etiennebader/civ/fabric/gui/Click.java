package de.etiennebader.civ.fabric.gui;

import net.minecraft.server.level.ServerPlayer;

/**
 * Ein Klick in einem Menü.
 *
 * @param right ob mit der rechten Maustaste geklickt wurde
 * @param shift ob dabei Umschalt gedrückt war
 */
public record Click(ServerPlayer player, boolean right, boolean shift) {

    /** 10 bei Umschalt, sonst 1: für Knöpfe, die Mengen verändern. */
    public int multiplier() {
        return shift ? 10 : 1;
    }
}
