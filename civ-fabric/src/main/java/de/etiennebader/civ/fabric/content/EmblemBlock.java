package de.etiennebader.civ.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Flagge, Wappen oder Wahlplakat als Block. Das Bild bzw. die Farbe steckt im Item und wird nach dem Setzen als
 * schwebende Anzeige gezeigt ({@link Emblems}); der Block selbst ist nur Ständer. Dass ein Block gesetzt wurde,
 * meldet {@link #setPlacedBy} an den {@link PlacementHook}; das ist zuverlässiger, als nach dem Rechtsklick zu raten,
 * ob der Block schon steht.
 */
public final class EmblemBlock extends Block {

    /** Welche Art Hoheitszeichen bzw. Werbung dieser Block ist; {@code COLUMN} ist die Litfaßsäule ohne Bild. */
    public enum Kind {
        FLAG, COAT_OF_ARMS, POSTER, COLUMN
    }

    /** Wird aufgerufen, sobald ein Spieler einen solchen Block gesetzt hat. */
    @FunctionalInterface
    public interface PlacementHook {
        void placed(ServerLevel level, BlockPos pos, Kind kind, LivingEntity placer, ItemStack stack);
    }

    private static volatile PlacementHook hook = (level, pos, kind, placer, stack) -> {
    };

    private final Kind kind;
    private final VoxelShape shape;

    public EmblemBlock(Properties properties, Kind kind, VoxelShape shape) {
        super(properties);
        this.kind = kind;
        this.shape = shape;
    }

    /** Legt fest, wer von gesetzten Blöcken erfährt; ersetzt den bisherigen Hook. */
    public static void onPlaced(PlacementHook placementHook) {
        hook = placementHook;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            hook.placed(serverLevel, pos, kind, placer, stack);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
