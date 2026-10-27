package de.etiennebader.civ.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Das Gemeindehaus: Wer es setzt, gründet eine Gemeinde. Der im Amboss vergebene Name des Blocks
 * wird der Name der Gemeinde, der Block ihr Sitz. Schlägt die Gründung fehl, verschwindet der
 * Block wieder und der Spieler bekommt ihn zurück.
 *
 * <p>Der Block ist im Überlebensmodus unzerstörbar und durch den Gebietsschutz gesichert.
 */
public final class TownHallBlock extends Block {

    /** Entscheidet, ob ein gesetztes Gemeindehaus stehen bleiben darf. */
    @FunctionalInterface
    public interface PlacementHandler {

        /**
         * @return ob die Gemeinde gegründet wurde; sonst wird der Block wieder entfernt
         */
        boolean placed(ServerPlayer player, BlockPos pos, ItemStack stack);
    }

    private final PlacementHandler placementHandler;

    public TownHallBlock(Properties properties, PlacementHandler placementHandler) {
        super(properties);
        this.placementHandler = Objects.requireNonNull(placementHandler, "placementHandler");
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (!placementHandler.placed(player, pos, stack)) {
            level.removeBlock(pos, false);
            if (!player.getAbilities().instabuild) {
                // Der Stapel wird erst nach diesem Aufruf verringert, deshalb eine Kopie zurückgeben.
                player.getInventory().placeItemBackInInventory(stack.copyWithCount(1), Prediction.SERVER_ONLY);
            }
        }
    }
}
