package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.fabric.WorldHooks;
import de.etiennebader.civ.server.civil.CivilService.SignType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Straßenschild, Hausnummer, Orts-, Länder- oder Nationenschild: trägt einen vom Spieler im
 * Amboss vergebenen Text (einzeilig, oder bei {@link #multiline()} mit {@code |} getrennt
 * mehrzeilig) sowie wahlweise eine per Farbstoff gesetzte Farbe.
 *
 * <p>Ohne Amboss-Namen wird die Beschriftung am Aufstellort ermittelt. Die Blockentität speichert und
 * synchronisiert sie mit dem Client; Rechtsklick zeigt sie zusätzlich in der Actionbar.
 */
public final class LabelBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Map<Direction, VoxelShape> STREET_SHAPES = Shapes.rotateHorizontal(
            Shapes.or(Block.box(1, 10, 7, 15, 15, 9), Block.box(2, 0, 7, 4, 10, 9)));
    private static final Map<Direction, VoxelShape> POST_SHAPES = Shapes.rotateHorizontal(
            Shapes.or(Block.box(1, 4, 7, 15, 15, 9), Block.box(7, 0, 7, 9, 4, 9)));

    /** 26.3 hat kein {@code DyeItem.getDyeColor()} mehr; Items.DYE ist eine ColorCollection ohne Rückweg. */
    private static final Map<Item, DyeColor> DYE_COLORS = Map.ofEntries(
            Map.entry(Items.DYE.white(), DyeColor.WHITE),
            Map.entry(Items.DYE.orange(), DyeColor.ORANGE),
            Map.entry(Items.DYE.magenta(), DyeColor.MAGENTA),
            Map.entry(Items.DYE.lightBlue(), DyeColor.LIGHT_BLUE),
            Map.entry(Items.DYE.yellow(), DyeColor.YELLOW),
            Map.entry(Items.DYE.lime(), DyeColor.LIME),
            Map.entry(Items.DYE.pink(), DyeColor.PINK),
            Map.entry(Items.DYE.gray(), DyeColor.GRAY),
            Map.entry(Items.DYE.lightGray(), DyeColor.LIGHT_GRAY),
            Map.entry(Items.DYE.cyan(), DyeColor.CYAN),
            Map.entry(Items.DYE.purple(), DyeColor.PURPLE),
            Map.entry(Items.DYE.blue(), DyeColor.BLUE),
            Map.entry(Items.DYE.brown(), DyeColor.BROWN),
            Map.entry(Items.DYE.green(), DyeColor.GREEN),
            Map.entry(Items.DYE.red(), DyeColor.RED),
            Map.entry(Items.DYE.black(), DyeColor.BLACK));

    private final Supplier<BlockEntityType<LabelBlockEntity>> entityType;
    private final int maxLines;
    private final SignType signType;

    public LabelBlock(Properties properties, Supplier<BlockEntityType<LabelBlockEntity>> entityType,
                      int maxLines, SignType signType) {
        super(properties);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.maxLines = maxLines;
        this.signType = Objects.requireNonNull(signType, "signType");
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    public SignType signType() {
        return signType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = signType == SignType.HOUSE && clicked.getAxis().isHorizontal()
                ? clicked : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (signType) {
            case HOUSE -> switch (facing) {
                case SOUTH -> Block.box(3, 4, 0, 13, 12, 2);
                case NORTH -> Block.box(3, 4, 14, 13, 12, 16);
                case EAST -> Block.box(0, 4, 3, 2, 12, 13);
                default -> Block.box(14, 4, 3, 16, 12, 13);
            };
            case STREET -> STREET_SHAPES.get(facing.getOpposite());
            case TOWN, COUNTRY, NATION -> POST_SHAPES.get(facing.getOpposite());
        };
    }

    public boolean multiline() {
        return maxLines > 1;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LabelBlockEntity entity)) {
            return;
        }
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            entity.setLines(WorldHooks.signLines(level, pos, signType), maxLines);
            return;
        }
        String raw = customName.getString();
        List<String> parsedLines = multiline() ? Arrays.asList(raw.split("\\|", maxLines)) : List.of(raw);
        entity.setLines(parsedLines, maxLines);
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                        net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LabelBlockEntity entity)) {
            return InteractionResult.SUCCESS;
        }
        DyeColor dyeColor = DYE_COLORS.get(stack.getItem());
        if (dyeColor != null) {
            entity.setColor(dyeColor);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("block." + de.etiennebader.civ.EddiesCivMod.MOD_ID
                        + ".label.recolored", colorName(dyeColor)), true);
            }
            return InteractionResult.CONSUME;
        }
        showLabel(player, entity);
        return InteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LabelBlockEntity entity)) {
            return InteractionResult.SUCCESS;
        }
        showLabel(player, entity);
        return InteractionResult.CONSUME;
    }

    private void showLabel(Player player, LabelBlockEntity entity) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String text = entity.lines().isEmpty()
                ? ""
                : entity.joinedLines();
        serverPlayer.sendSystemMessage(Component.literal(text), true);
    }

    private static String colorName(DyeColor color) {
        return color.getSerializedName();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabelBlockEntity(entityType.get(), pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        return null;
    }
}
