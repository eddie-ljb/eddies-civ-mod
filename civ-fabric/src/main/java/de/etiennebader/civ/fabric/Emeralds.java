package de.etiennebader.civ.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Smaragde im Inventar eines Spielers: zählen, abnehmen und auszahlen. Nur unveränderte Smaragde zählen,
 * umbenannte oder anderweitig veränderte bleiben unberührt.
 */
public final class Emeralds {

    private Emeralds() {
    }

    public static int count(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isPlainEmerald(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Nimmt bis zu {@code wanted} Smaragde aus dem Inventar.
     *
     * @return wie viele tatsächlich abgenommen wurden
     */
    public static int take(ServerPlayer player, int wanted) {
        int remaining = wanted;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (remaining <= 0) {
                break;
            }
            if (isPlainEmerald(stack)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        player.containerMenu.broadcastChanges();
        return wanted - remaining;
    }

    /** Gibt Smaragde ins Inventar; was nicht passt, fällt vor dem Spieler auf den Boden. */
    public static void give(ServerPlayer player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(remaining, Items.EMERALD.getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, stack), Prediction.SERVER_ONLY);
            remaining -= stack;
        }
        player.containerMenu.broadcastChanges();
    }

    private static boolean isPlainEmerald(ItemStack stack) {
        return stack.is(Items.EMERALD) && stack.getComponentsPatch().isEmpty();
    }
}
