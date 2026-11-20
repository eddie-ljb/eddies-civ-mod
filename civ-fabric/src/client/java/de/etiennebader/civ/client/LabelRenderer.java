package de.etiennebader.civ.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.etiennebader.civ.fabric.content.LabelBlock;
import de.etiennebader.civ.fabric.content.LabelBlockEntity;
import de.etiennebader.civ.server.civil.CivilService.SignType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Zeichnet den synchronisierten Text auf die Vorderseite des jeweiligen Schildmodells. */
final class LabelRenderer implements BlockEntityRenderer<LabelBlockEntity, LabelRenderer.State> {
    private static final float SURFACE_OFFSET = 0.0015F;
    private final Font font;

    LabelRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    static final class State extends BlockEntityRenderState {
        List<FormattedCharSequence> lines = List.of();
        SignType type;
        float yaw;
        int color;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LabelBlockEntity entity, State target, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(entity, target, partialTick, camera, overlay);
        var blockState = entity.getBlockState();
        target.type = ((LabelBlock) blockState.getBlock()).signType();
        target.yaw = -blockState.getValue(LabelBlock.FACING).toYRot();
        target.lines = entity.lines().stream().map(line -> Component.literal(line).getVisualOrderText()).toList();
        target.color = entity.color() != null ? 0xff000000 | entity.color().getTextColor()
                : switch (target.type) {
                    case STREET, HOUSE -> 0xfff6f6f6;
                    case TOWN, COUNTRY, NATION -> 0xff201a13;
                };
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.lines.isEmpty()) {
            return;
        }
        float centerY = switch (state.type) {
            case STREET -> 12.5F;
            case HOUSE -> 8F;
            case TOWN, COUNTRY, NATION -> 9.5F;
        };
        float width = state.type == SignType.HOUSE ? 7F : 10F;
        float height = switch (state.type) {
            case STREET -> 3.5F;
            case HOUSE -> 5.5F;
            case TOWN, COUNTRY, NATION -> 8F;
        };
        float front = state.type == SignType.HOUSE ? 2F : 9F;
        int lineStep = font.lineHeight + 2;
        int textHeight = (state.lines.size() - 1) * lineStep + font.lineHeight;
        int textWidth = state.lines.stream().mapToInt(font::width).max().orElse(0);
        float scale = Math.min(width / Math.max(textWidth, 1), height / textHeight) / 16F;

        pose.pushPose();
        pose.translate(0.5F, centerY / 16F, 0.5F);
        pose.rotateDegrees(Axis.YP, state.yaw);
        pose.translate(0F, 0F, front / 16F - 0.5F + SURFACE_OFFSET);
        pose.scale(scale, -scale, scale);
        for (int i = 0; i < state.lines.size(); i++) {
            FormattedCharSequence line = state.lines.get(i);
            collector.submitText(pose, -font.width(line) / 2F, i * lineStep - textHeight / 2F, line,
                    false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, state.color, 0, 0);
        }
        pose.popPose();
    }
}
