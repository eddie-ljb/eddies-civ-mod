package de.etiennebader.civ.fabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.content.CivContent;
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
import java.util.HashSet;
import java.util.Set;
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
    private static final int MAX_FOUNDATION = 24;

    /** Baustellen, deren Absperrung schon abgebaut wurde. */
    private final Set<UUID> cleared = new HashSet<>();

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
        fences(server, civ);
    }

    // ------------------------------------------------------------------ Absperrung

    private static Block barrier() {
        return BuiltInRegistries.BLOCK.getValue(CivContent.CONSTRUCTION_BARRIER_ID);
    }

    /**
     * Die Bauabsperrung: Um jede laufende Baustelle steht ein Zaun (ein Block Abstand zum Bau, höchstens bis zur
     * Grundstücksgrenze). Fertige oder abgebrochene Baustellen bekommen ihn abgenommen.
     */
    private void fences(MinecraftServer server, CivServer civ) {
        for (ConstructionService.Site site : civ.construction().sites()) {
            if (!site.active() && cleared.contains(site.project())) {
                continue;
            }
            Optional<ServerLevel> found = levelOf(server, site.min());
            if (found.isEmpty()) {
                continue;
            }
            ServerLevel level = found.get();
            int x1 = Math.max(site.limit().minX(), site.min().x() - 1);
            int z1 = Math.max(site.limit().minZ(), site.min().z() - 1);
            int x2 = Math.min(site.limit().maxX(), site.min().x() + site.sizeX());
            int z2 = Math.min(site.limit().maxZ(), site.min().z() + site.sizeZ());
            if (!level.isLoaded(new BlockPos(x1, 64, z1)) || !level.isLoaded(new BlockPos(x2, 64, z2))) {
                continue;
            }
            Block barrier = barrier();
            boolean complete = true;
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (x != x1 && x != x2 && z != z1 && z != z2) {
                        continue;
                    }
                    if (site.active()) {
                        complete &= raise(level, barrier, x, z);
                    } else {
                        remove(level, barrier, x, z, site.min().y());
                    }
                }
            }
            if (!site.active()) {
                cleared.add(site.project());
            }
        }
    }

    /** Stellt an der Stelle einen Zaun auf den Boden, wenn dort Platz ist. */
    private static boolean raise(ServerLevel level, Block barrier, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState here = level.getBlockState(pos);
        if (here.is(barrier)) {
            return true;
        }
        if (!replaceable(here) || level.getBlockState(pos.below()).isAir()) {
            return false;
        }
        level.setBlock(pos, barrier.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** Nimmt den Zaun in der Spalte weg, wo immer er steht (Gelände kann sich geändert haben). */
    private static void remove(ServerLevel level, Block barrier, int x, int z, int baseY) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2;
        for (int y = Math.max(level.getMinY(), baseY - 8); y <= top; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).is(barrier)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
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
            public int floorLevel(BlockPosition corner, int sizeX, int sizeZ, Optional<BlockPosition> doorFront,
                                  int doorHeight) {
                ServerLevel level = levelOf(server, corner).orElseThrow();
                if (doorFront.isPresent()) {
                    // Die Tür liegt auf Straßenhöhe: Der Boden vor der Tür ist der Maßstab.
                    return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, doorFront.get().x(),
                            doorFront.get().z()) - doorHeight;
                }
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, corner.x() + sizeX / 2,
                        corner.z() + sizeZ / 2) - 1;
            }

            @Override
            public boolean isClear(BlockPosition min, int sizeX, int sizeY, int sizeZ) {
                ServerLevel level = levelOf(server, min).orElseThrow();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int y = 0; y < sizeY; y++) {
                    for (int x = 0; x < sizeX; x++) {
                        for (int z = 0; z < sizeZ; z++) {
                            pos.set(min.x() + x, min.y() + y, min.z() + z);
                            BlockState state = level.getBlockState(pos);
                            if (!replaceable(state) && !natural(state)) {
                                return false; // Gebautes bleibt stehen; Erde und Stein werden abgetragen
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
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(barrier());
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
        if (build.first()) {
            excavate(level, build);
        }
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

    /**
     * Trägt vor dem ersten Stein das Gelände im Baufeld ab: Erde, Stein und Bewuchs über der untersten Ebene
     * verschwinden, damit das Haus eben auf Straßenhöhe steht und nicht im Hang steckt.
     */
    private static void excavate(ServerLevel level, ConstructionService.Build build) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < build.sizeY(); y++) {
            for (int x = 0; x < build.footprintX(); x++) {
                for (int z = 0; z < build.footprintZ(); z++) {
                    pos.set(build.origin().x() + x, build.origin().y() + y, build.origin().z() + z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.BEDROCK) && (replaceable(state) || natural(state))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
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
        int x1 = build.origin().x() - 1;
        int z1 = build.origin().z() - 1;
        int x2 = build.origin().x() + build.footprintX();
        int z2 = build.origin().z() + build.footprintZ();
        for (UUID id : build.workers()) {
            if (level.getEntity(id) instanceof Villager worker) {
                BlockPos at = worker.blockPosition();
                if (at.getX() < x1 || at.getX() > x2 || at.getZ() < z1 || at.getZ() > z2) {
                    // Die Absperrung hält Unbefugte draußen; die Bauarbeiter gehören dazu und kommen so hinein.
                    if (at.distSqr(site) < 40 * 40) {
                        worker.teleportTo(site.getX() + 0.5, site.getY() + 1.0, site.getZ() + 0.5);
                    }
                }
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
