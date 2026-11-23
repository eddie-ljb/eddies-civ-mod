package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.domain.production.MachineType;
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
 * Block einer Firmenmaschine. Beim Aufstellen prüft das Spiel, ob die Maschine zur Firma des Spielers gehört und
 * auf deren Grundstück steht; sonst verschwindet der Block und der Spieler bekommt ihn zurück.
 */
public final class MachineBlock extends Block {

    /** Entscheidet, ob eine aufgestellte Maschine stehen bleiben darf. */
    @FunctionalInterface
    public interface PlacementHandler {
        boolean placed(ServerPlayer player, BlockPos pos, MachineType type);
    }

    private final MachineType type;
    private final PlacementHandler handler;

    public MachineBlock(Properties properties, MachineType type, PlacementHandler handler) {
        super(properties);
        this.type = Objects.requireNonNull(type, "type");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public MachineType type() {
        return type;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (!handler.placed(player, pos, type)) {
            level.removeBlock(pos, false);
            if (!player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(stack.copyWithCount(1), Prediction.SERVER_ONLY);
            }
        }
    }
}
