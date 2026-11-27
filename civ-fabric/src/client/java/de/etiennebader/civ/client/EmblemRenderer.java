package de.etiennebader.civ.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.fabric.content.EmblemBlock;
import de.etiennebader.civ.fabric.content.EmblemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Malt das Bild von Flagge, Wappen und Wahlplakat direkt auf das Modell: Tuch am Mast, Schild am Pfosten, Plakat im
 * Rahmen. Anders als eine Karte hat es keinen Papierrand. Das PNG kommt mit dem Block-Entity vom Server und wird einmal
 * zur Textur; leere und einfarbige Zeichen färben eine weiße Fläche ein.
 */
final class EmblemRenderer implements BlockEntityRenderer<EmblemBlockEntity, EmblemRenderer.State> {

    private static final Identifier WHITE = EddiesCivMod.id("emblem/white");
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static boolean whiteReady;

    EmblemRenderer(BlockEntityRendererProvider.Context context) {
    }

    static final class State extends BlockEntityRenderState {
        EmblemBlock.Kind kind = EmblemBlock.Kind.FLAG;
        float yaw;
        String style = "blank";
        byte[] png = new byte[0];
    }

    /** Mitte (in Pixeln im nach Süden gezeichneten Modell) und Größe (in Blöcken) des Bildes je Art. */
    private record Layout(float x, float y, float z, float width, float height) {
    }

    private static Layout layout(EmblemBlock.Kind kind) {
        return switch (kind) {
            case FLAG -> new Layout(9, 10, 8, 0.75F, 0.5F);
            case COAT_OF_ARMS -> new Layout(8, 7.5F, 3.2F, 0.625F, 0.8125F);
            default -> new Layout(8, 9.5F, 9.05F, 0.5F, 0.6875F);
        };
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EmblemBlockEntity entity, State target, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(entity, target, partialTick, camera, overlay);
        var blockState = entity.getBlockState();
        target.kind = ((EmblemBlock) blockState.getBlock()).kind();
        target.yaw = -blockState.getValue(EmblemBlock.FACING).toYRot();
        target.style = entity.style();
        target.png = entity.png();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        Layout layout = layout(state.kind);
        Identifier texture = WHITE;
        int color = tint(state);
        if (state.png.length > 0) {
            Identifier image = imageTexture(state.png);
            if (image != null) {
                texture = image;
                color = 0xFFFFFFFF;
            }
        }
        ensureWhite();
        final int argb = color;
        final int light = state.lightCoords;
        final float halfWidth = layout.width() / 2;
        final float halfHeight = layout.height() / 2;

        pose.pushPose();
        pose.translate(0.5F, 0F, 0.5F);
        pose.rotateDegrees(Axis.YP, state.yaw);
        pose.translate((layout.x() - 8) / 16F, layout.y() / 16F, (layout.z() - 8) / 16F);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (entry, consumer) -> {
            float[][] corners = {{-halfWidth, -halfHeight, 0, 1}, {halfWidth, -halfHeight, 1, 1},
                    {halfWidth, halfHeight, 1, 0}, {-halfWidth, halfHeight, 0, 0}};
            for (float[] corner : corners) {
                consumer.addVertex(entry, corner[0], corner[1], 0F).setColor(argb).setUv(corner[2], corner[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, 0F, 0F, 1F);
            }
        });
        pose.popPose();
    }

    /** Farbe der Fläche ohne Bild: die gewählte Farbe, sonst das Weiß der leeren Art. */
    private static int tint(State state) {
        if (state.style.startsWith("color:")) {
            return 0xFF000000 | DyeColor.byName(state.style.substring("color:".length()), DyeColor.WHITE)
                    .getMapColor().col;
        }
        return switch (state.kind) {
            case COAT_OF_ARMS -> 0xFFC8C8D0;
            case POSTER -> 0xFFF0EBD8;
            default -> 0xFFF5F5F5;
        };
    }

    private static void ensureWhite() {
        if (whiteReady) {
            return;
        }
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, 0xFFFFFFFF);
        Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "emblem white", image));
        whiteReady = true;
    }

    /** Die Textur zu diesen PNG-Bytes; entsteht beim ersten Mal. Null, wenn die Bytes kein Bild sind. */
    private static Identifier imageTexture(byte[] png) {
        String key = png.length + "_" + Arrays.hashCode(png);
        if (TEXTURES.containsKey(key)) {
            return TEXTURES.get(key);
        }
        Identifier id = null;
        try {
            NativeImage image = NativeImage.read(png);
            id = EddiesCivMod.id("emblem/" + key);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "emblem " + key, image));
        } catch (IOException | RuntimeException e) {
            EddiesCivMod.LOGGER.warn("Bild eines Zeichens konnte nicht gelesen werden", e);
        }
        TEXTURES.put(key, id);
        return id;
    }
}
