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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Setzt die Blöcke der Bauaufträge (Issue #19) sichtbar Stück für Stück in die Welt. Alle {@value #INTERVAL} Ticks
 * fragt es den {@link ConstructionService}, was die Bauarbeiter seither geschafft haben, und setzt das in geladenen
 * Chunks. Auf nicht geladenem Gelände ruht die Baustelle.
 */
final class ConstructionSites {

    private static final int INTERVAL = 40;

    private final Map<String, BlockState> parsed = new HashMap<>();
    private int ticks;

    void tick(MinecraftServer server, CivServer civ) {
        if (++ticks < INTERVAL) {
            return;
        }
        long elapsed = ticks;
        ticks = 0;
        for (ConstructionService.Build build : civ.construction().advance(elapsed,
                position -> isLoaded(server, position), position -> surface(server, position))) {
            place(server, build);
        }
    }

    private static Optional<ServerLevel> levelOf(MinecraftServer server, BlockPosition position) {
        Identifier dimension = Identifier.tryParse(position.dimension().value());
        return dimension == null ? Optional.empty()
                : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)));
    }

    private static boolean isLoaded(MinecraftServer server, BlockPosition position) {
        return levelOf(server, position).map(level -> level.isLoaded(new BlockPos(position.x(), 64, position.z())))
                .orElse(false);
    }

    private static int surface(MinecraftServer server, BlockPosition position) {
        return levelOf(server, position)
                .map(level -> level.getHeight(Heightmap.Types.WORLD_SURFACE, position.x(), position.z()))
                .orElse(64);
    }

    private void place(MinecraftServer server, ConstructionService.Build build) {
        Optional<ServerLevel> found = levelOf(server, build.origin());
        if (found.isEmpty()) {
            return;
        }
        ServerLevel level = found.get();
        BlockPos last = null;
        for (Blueprint.Placement placement : build.blocks()) {
            BlockState state = stateOf(placement.state());
            BlockPos pos = new BlockPos(build.origin().x() + placement.x(), build.origin().y() + placement.y(),
                    build.origin().z() + placement.z());
            if (level.isLoaded(pos) && state != null) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                last = pos;
            }
        }
        if (last != null) {
            level.sendParticles(ParticleTypes.CLOUD, last.getX() + 0.5, last.getY() + 1.0, last.getZ() + 0.5, 6, 0.4,
                    0.2, 0.4, 0.01);
        }
    }

    /** Wandelt einen Blockzustand als Text in einen Zustand um; Unbekanntes wird übersprungen. */
    private BlockState stateOf(String text) {
        return parsed.computeIfAbsent(text, key -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, key, false).blockState();
            } catch (CommandSyntaxException | RuntimeException e) {
                return null;
            }
        });
    }
}
