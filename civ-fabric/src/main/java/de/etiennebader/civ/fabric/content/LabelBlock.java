package de.etiennebader.civ.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Straßenschild, Hausnummer, Orts-, Länder- oder Nationenschild: trägt einen vom Spieler im
 * Amboss vergebenen Text (einzeilig, oder bei {@link #multiline} mit {@code |} getrennt
 * mehrzeilig) sowie wahlweise eine per Farbstoff gesetzte Farbe.
 *
 * <p>Der Text wird serverseitig in der {@link LabelBlockEntity} gespeichert. Eine echte
 * 3D-Textdarstellung im Spiel (wie bei Vanilla-Schildern) ist in dieser Ausbaustufe bewusst nicht
 * umgesetzt (siehe {@code docs/issue-11-strassenschilder-hausnummern.md}); stattdessen zeigt ein
 * Rechtsklick den aktuellen Text in der Actionbar.
 */
public final class LabelBlock extends Block implements EntityBlock {

    private final Supplier<BlockEntityType<LabelBlockEntity>> entityType;
    private final int maxLines;

    public LabelBlock(Properties properties, Supplier<BlockEntityType<LabelBlockEntity>> entityType, int maxLines) {
        super(properties);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.maxLines = maxLines;
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
        if (stack.getItem() instanceof DyeItem dyeItem) {
            entity.setColor(dyeItem.getDyeColor());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("block." + de.etiennebader.civ.EddiesCivMod.MOD_ID
                    + ".label.recolored", colorName(dyeItem.getDyeColor())), true);
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
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        String text = entity.lines().isEmpty()
                ? ""
                : entity.joinedLines();
        player.displayClientMessage(Component.literal(text), true);
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
