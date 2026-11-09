package de.etiennebader.civ.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Kisten als Läden: Inhalt finden, Lager zählen und Ware an Käufer übergeben. Doppelkisten zählen als eine.
 */
public final class ShopChests {

    private static final double REACH = 5.0;

    private ShopChests() {
    }

    /** Der Behälter an dieser Stelle, falls es einer ist. */
    public static Optional<Container> containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return Optional.ofNullable(ChestBlock.getContainer(chest, state, level, pos, true));
        }
        return level.getBlockEntity(pos) instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    /** Der Behälter, auf den der Spieler schaut. */
    public static Optional<BlockPos> lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return Optional.empty();
        }
        BlockPos pos = blockHit.getBlockPos();
        return containerAt(player.level(), pos).map(container -> pos);
    }

    /** Wie viele unveränderte Stücke des Items im Behälter liegen. */
    public static int stock(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Nimmt die Menge aus dem Behälter und gibt sie dem Spieler; Überschuss fällt vor ihm zu Boden. */
    public static void deliver(Container container, Item item, int quantity, ServerPlayer buyer) {
        int remaining = quantity;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                ItemStack taken = container.removeItem(slot, Math.min(remaining, stack.getCount()));
                remaining -= taken.getCount();
                buyer.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
            }
        }
        container.setChanged();
        buyer.containerMenu.broadcastChanges();
    }
}
