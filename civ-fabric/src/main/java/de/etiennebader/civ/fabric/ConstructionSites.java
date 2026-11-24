package de.etiennebader.civ.fabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.construction.ConstructionService;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Setzt die Blöcke der Bauaufträge (Issue #19) sichtbar Stück für Stück in die Welt. Alle {@value #INTERVAL} Ticks
 * fragt es den {@link ConstructionService}, was die Bauarbeiter seither geschafft haben, und setzt das in geladenen
 * Chunks. Die Bauarbeiter (angestellte Dorfbewohner) laufen zur Baustelle und schwingen den Arm. Unter der untersten
 * Ebene wird bis zum Boden ein Fundament aus demselben Block gegossen. Nichts Gebautes wird überschrieben: das
 * Baufeld muss frei sein, bevor die Arbeit beginnt, und einzelne Blöcke werden nur über Luft, Pflanzen, Laub und
 * Holz gesetzt.
 */
final class ConstructionSites {

    private static final int INTERVAL = 40;
    private static final int MAX_FOUNDATION = 12;

    private final Map<String, BlockState> parsed = new HashMap<>();
    private int ticks;

    void tick(MinecraftServer server, CivServer civ) {
        if (++ticks < INTERVAL) {
            return;
        }
        long elapsed = ticks;
        ticks = 0;
        civ.construction().siteSurvey(survey(server));
        for (ConstructionService.Build build : civ.construction().advance(elapsed, terrain(server))) {
            place(server, build);
        }
    }

    private static Optional<ServerLevel> levelOf(MinecraftServer server, BlockPosition position) {
        Identifier dimension = Identifier.tryParse(position.dimension().value());
        return dimension == null ? Optional.empty()
                : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)));
    }

    /**
     * Ob auf einer Fläche schon gebaut wurde, auch ohne Bauauftrag: In jeder Spalte zählen die obersten Blöcke, die
     * weder Luft, Flüssigkeit, Pflanzen, Laub, Holz noch natürliches Gelände (Erde, Stein, Sand, Schnee …) sind.
     * Liegt ein Teil der Fläche in einem nicht geladenen Chunk, ist das Ergebnis offen.
     */
    private static ConstructionService.SiteSurvey survey(MinecraftServer server) {
        return area -> {
            Optional<ServerLevel> found = levelOf(server, new BlockPosition(area.dimension(), area.minX(), 0, area.minZ()));
            if (found.isEmpty()) {
                return Optional.empty();
            }
            ServerLevel level = found.get();
            for (int x = area.minX(); x <= area.maxX() + 15; x += 16) {
                for (int z = area.minZ(); z <= area.maxZ() + 15; z += 16) {
                    if (!level.isLoaded(new BlockPos(Math.min(x, area.maxX()), 64, Math.min(z, area.maxZ())))) {
                        return Optional.empty();
                    }
                }
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int y = top - 1; y >= Math.max(level.getMinY(), top - 6); y--) {
                        pos.set(x, y, z);
                        if (!natural(level.getBlockState(pos))) {
                            return Optional.of(true);
                        }
                    }
                }
            }
            return Optional.of(false);
        };
    }

    private static boolean natural(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.BEDROCK);
    }

    private static ConstructionService.Terrain terrain(MinecraftServer server) {
        return new ConstructionService.Terrain() {
            @Override
            public boolean isLoaded(BlockPosition position) {
                return levelOf(server, position)
                        .map(level -> level.isLoaded(new BlockPos(position.x(), 64, position.z()))).orElse(false);
            }

            @Override
            public String blockName(BlockPosition position) {
                return levelOf(server, position).map(level -> {
                    BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
                    return level.isLoaded(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                            .toString() : null;
                }).orElse(null);
            }

            @Override
            public int groundLevel(BlockPosition min, int sizeX, int sizeZ) {
                ServerLevel level = levelOf(server, min).orElseThrow();
                int highest = level.getMinY();
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        highest = Math.max(highest, level.getHeight(Heightmap.Types.WORLD_SURFACE, min.x() + x,
                                min.z() + z));
                    }
                }
                return highest;
            }

            @Override
            public boolean isClear(BlockPosition min, int sizeX, int sizeY, int sizeZ) {
                ServerLevel level = levelOf(server, min).orElseThrow();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int y = 0; y < sizeY; y++) {
                    for (int x = 0; x < sizeX; x++) {
                        for (int z = 0; z < sizeZ; z++) {
                            pos.set(min.x() + x, min.y() + y, min.z() + z);
                            if (!replaceable(level.getBlockState(pos))) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
        };
    }

    /** Luft, Pflanzen und andere ersetzbare Blöcke, Laub und Holz (Bäume) darf ein Bau verdrängen. */
    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    private void place(MinecraftServer server, ConstructionService.Build build) {
        Optional<ServerLevel> found = levelOf(server, build.origin());
        if (found.isEmpty()) {
            return;
        }
        ServerLevel level = found.get();
        Rotation rotation = switch (build.rotation()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        BlockPos last = null;
        for (Blueprint.Placement placement : build.blocks()) {
            BlockState state = stateOf(placement.state());
            BlockPos pos = new BlockPos(build.origin().x() + placement.x(), build.origin().y() + placement.y(),
                    build.origin().z() + placement.z());
            if (state == null || !level.isLoaded(pos) || !replaceable(level.getBlockState(pos))) {
                continue;
            }
            BlockState turned = state.rotate(rotation);
            level.setBlock(pos, turned, Block.UPDATE_ALL);
            if (placement.y() == 0) {
                foundation(level, pos, turned);
            }
            last = pos;
        }
        if (last != null) {
            level.sendParticles(ParticleTypes.CLOUD, last.getX() + 0.5, last.getY() + 1.0, last.getZ() + 0.5, 6, 0.4,
                    0.2, 0.4, 0.01);
            moveWorkers(level, build, last);
        }
    }

    /** Füllt unter der untersten Ebene bis zum festen Boden auf, damit nichts in der Luft hängt. */
    private static void foundation(ServerLevel level, BlockPos floor, BlockState state) {
        if (!state.isSolid()) {
            return;
        }
        BlockPos below = floor.below();
        for (int depth = 0; depth < MAX_FOUNDATION && level.isLoaded(below)
                && replaceable(level.getBlockState(below)); depth++) {
            level.setBlock(below, state, Block.UPDATE_ALL);
            below = below.below();
        }
    }

    /** Die Bauarbeiter gehen zur zuletzt gebauten Stelle und schwingen den Arm. */
    private static void moveWorkers(ServerLevel level, ConstructionService.Build build, BlockPos site) {
        for (UUID id : build.workers()) {
            if (level.getEntity(id) instanceof Villager worker) {
                if (worker.blockPosition().distSqr(site) > 9) {
                    worker.getNavigation().moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0.6);
                } else {
                    worker.swingForAttack(InteractionHand.MAIN_HAND);
                }
            }
        }
    }

    /** Wandelt einen Blockzustand als Text in einen Zustand um; Unbekanntes wird übersprungen. */
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

    /**
     * Nimmt dem Spieler die Baustoffe ab, die ein Auftrag mit eigenem Material noch braucht.
     *
     * @return wie viele Stück abgegeben wurden
     */
    static int deliver(ServerPlayer player, CivServer civ, int number) {
        int total = 0;
        for (Map.Entry<String, Integer> need : civ.construction().missing(number).entrySet()) {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.isEmpty()
                        || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(need.getKey())) {
                    continue;
                }
                int taken = civ.construction().deliver(Players.ref(player), number, need.getKey(), stack.getCount());
                stack.shrink(taken);
                total += taken;
            }
        }
        return total;
    }
}
