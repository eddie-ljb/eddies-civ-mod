package de.etiennebader.civ.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

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

    /** Die Vorderseite zeigt zum Spieler, der den Block gesetzt hat; das Modell ist nach Süden gezeichnet. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Kind kind;
    private final Map<Direction, VoxelShape> shapes;

    public EmblemBlock(Properties properties, Kind kind, VoxelShape shape) {
        super(properties);
        this.kind = kind;
        this.shapes = Shapes.rotateHorizontal(shape);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }
}
