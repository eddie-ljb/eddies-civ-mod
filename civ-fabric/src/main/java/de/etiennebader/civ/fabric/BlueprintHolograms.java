package de.etiennebader.civ.fabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.math.Transformation;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.server.construction.ConstructionService;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hologramm eines Bauplans: Der Spieler sieht das Bauwerk, wie es auf seinem Grundstück stünde, bevor (und während) er
 * es beauftragt. Gezeichnet wird mit Block-Anzeigen (Display-Entities), die nur an diesen Spieler gesendet werden:
 * Es steht nichts in der Welt, nichts wird gespeichert, und es braucht keinen Mod auf dem Client. Jeder Block
 * leuchtet bläulich, damit er sich vom echten Gelände abhebt. Nach {@value #DURATION} Ticks (45 Sekunden), beim
 * Dimensionswechsel oder auf Befehl verschwindet es wieder.
 *
 * <p>Gezeigt werden nur Blöcke, die an Luft oder den Rand des Plans grenzen (das Innere sieht man nie); bei sehr
 * großen Plänen höchstens {@value #MAX_BLOCKS}, gleichmäßig verteilt. Dazu zeichnen Partikel die Grundfläche nach.
 */
final class BlueprintHolograms {

    static final int DURATION = 900;
    static final int MAX_BLOCKS = 2500;
    private static final int INTERVAL = 10;
    private static final int GLOW = 0x66CCFF;
    private static final float SCALE = 0.96F;
    private static final float INSET = (1.0F - SCALE) / 2;
    private static final DustParticleOptions OUTLINE = new DustParticleOptions(GLOW, 1.2F);

    /** Was ein Spieler gerade sieht. */
    private record Shown(String dimension, IntList ids, int remaining, double minX, double minZ, double maxX,
                         double maxZ, double y) {

        Shown aged() {
            return new Shown(dimension, ids, remaining - INTERVAL, minX, minZ, maxX, maxZ, y);
        }
    }

    private final Map<UUID, Shown> shown = new HashMap<>();
    private final Map<String, BlockState> parsed = new HashMap<>();
    private int ticks;

    /** Blendet das Hologramm des Spielers ein; ein bisheriges verschwindet. */
    void show(ServerPlayer player, ConstructionService.Preview preview) {
        clear(player);
        Blueprint plan = preview.plan();
        Rotation rotation = switch (preview.rotation() % 4) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        IntList ids = new IntArrayList();
        for (Blueprint.Placement placement : visible(plan)) {
            BlockState state = stateOf(placement.state());
            if (state == null) {
                continue;
            }
            Blueprint.Placement turned = plan.rotated(placement, preview.rotation());
            BlockPos pos = new BlockPos(preview.origin().x() + turned.x(), preview.origin().y() + turned.y(),
                    preview.origin().z() + turned.z());
            Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, player.level());
            display.setPos(pos.getX(), pos.getY(), pos.getZ());
            display.setBlockState(state.rotate(rotation));
            display.setTransformation(new Transformation(new Vector3f(INSET, INSET, INSET), null,
                    new Vector3f(SCALE, SCALE, SCALE), null));
            display.setViewRange(2.0F);
            display.setGlowColorOverride(GLOW);
            display.setGlowingTag(true);
            player.connection.send(new ClientboundAddEntityPacket(display, 0, pos));
            List<SynchedEntityData.DataValue<?>> data = display.getEntityData().getNonDefaultValues();
            if (data != null) {
                player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
            }
            ids.add(display.getId());
        }
        int footprintX = plan.footprintX(preview.rotation());
        int footprintZ = plan.footprintZ(preview.rotation());
        shown.put(player.getUUID(), new Shown(player.level().dimension().identifier().toString(), ids, DURATION,
                preview.origin().x(), preview.origin().z(), preview.origin().x() + footprintX,
                preview.origin().z() + footprintZ, preview.origin().y()));
    }

    /** Blendet das Hologramm des Spielers aus. @return ob eines zu sehen war */
    boolean clear(ServerPlayer player) {
        Shown removed = shown.remove(player.getUUID());
        if (removed == null) {
            return false;
        }
        remove(player, removed);
        return true;
    }

    void tick(MinecraftServer server) {
        if (shown.isEmpty() || ++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        for (Map.Entry<UUID, Shown> entry : new ArrayList<>(shown.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Shown current = entry.getValue().aged();
            if (player == null) {
                shown.remove(entry.getKey());
            } else if (current.remaining() <= 0
                    || !current.dimension().equals(player.level().dimension().identifier().toString())) {
                shown.remove(entry.getKey());
                remove(player, current);
            } else {
                shown.put(entry.getKey(), current);
                outline(player, current);
            }
        }
    }

    private static void remove(ServerPlayer player, Shown hologram) {
        if (!hologram.ids().isEmpty()) {
            player.connection.send(new ClientboundRemoveEntitiesPacket(hologram.ids()));
        }
    }

    /** Die Grundfläche des Plans als Partikellinie knapp über dem Boden. */
    private static void outline(ServerPlayer player, Shown hologram) {
        double y = hologram.y() + 0.1;
        for (double x = hologram.minX(); x <= hologram.maxX(); x += 1.0) {
            particle(player, x, y, hologram.minZ());
            particle(player, x, y, hologram.maxZ());
        }
        for (double z = hologram.minZ() + 1; z < hologram.maxZ(); z += 1.0) {
            particle(player, hologram.minX(), y, z);
            particle(player, hologram.maxX(), y, z);
        }
    }

    private static void particle(ServerPlayer player, double x, double y, double z) {
        player.level().sendParticles(player, OUTLINE, true, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Die Blöcke, die man von außen sieht: solche mit mindestens einer Seite an Luft oder am Rand des Plans. Sind es
     * mehr als {@value #MAX_BLOCKS}, kommt nur jeder {@code n}-te in die Auswahl.
     */
    static List<Blueprint.Placement> visible(Blueprint plan) {
        int[] blocks = plan.blocks();
        int sx = plan.sizeX();
        int sy = plan.sizeY();
        int sz = plan.sizeZ();
        List<Blueprint.Placement> exposed = new ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int index = blocks[(y * sz + z) * sx + x];
                    if (index == 0 || Blueprint.isEmpty(plan.palette().get(index))) {
                        continue;
                    }
                    if (open(blocks, sx, sy, sz, x - 1, y, z) || open(blocks, sx, sy, sz, x + 1, y, z)
                            || open(blocks, sx, sy, sz, x, y - 1, z) || open(blocks, sx, sy, sz, x, y + 1, z)
                            || open(blocks, sx, sy, sz, x, y, z - 1) || open(blocks, sx, sy, sz, x, y, z + 1)) {
                        exposed.add(new Blueprint.Placement(x, y, z, plan.palette().get(index)));
                    }
                }
            }
        }
        if (exposed.size() <= MAX_BLOCKS) {
            return exposed;
        }
        List<Blueprint.Placement> thinned = new ArrayList<>(MAX_BLOCKS);
        double step = (double) exposed.size() / MAX_BLOCKS;
        for (int i = 0; i < MAX_BLOCKS; i++) {
            thinned.add(exposed.get((int) (i * step)));
        }
        return thinned;
    }

    /** Ob an der Stelle nichts steht: außerhalb des Plans oder Luft. */
    private static boolean open(int[] blocks, int sx, int sy, int sz, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return true;
        }
        return blocks[(y * sz + z) * sx + x] == 0;
    }

    private BlockState stateOf(String text) {
        BlockState cached = parsed.get(text);
        if (cached != null) {
            return cached;
        }
        try {
            BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
            parsed.put(text, state);
            return state;
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }
}
