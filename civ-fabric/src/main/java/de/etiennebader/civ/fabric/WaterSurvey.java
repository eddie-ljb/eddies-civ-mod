package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.land.LandRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/**
 * Misst, wie viel einer Fläche Wasser ist: in jeder zweiten Spalte zählt, ob die Oberfläche Wasser ist (auch zugefrorenes,
 * also Eis über Wasser). Nicht geladenes Gelände ist unbekannt.
 */
final class WaterSurvey {

    private static final int STEP = 2;

    private WaterSurvey() {
    }

    static LandRules.Survey of(MinecraftServer server) {
        return area -> {
            Identifier dimension = Identifier.tryParse(area.dimension().value());
            ServerLevel level = dimension == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            return level == null ? Optional.empty() : measure(level, area);
        };
    }

    static Optional<Integer> measure(ServerLevel level, Area area) {
        if (!ChunkLoader.ensureArea(level, area.minX(), area.minZ(), area.maxX(), area.maxZ(), false)) {
            return Optional.empty();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int water = 0;
        int total = 0;
        for (int x = area.minX(); x <= area.maxX(); x += STEP) {
            for (int z = area.minZ(); z <= area.maxZ(); z += STEP) {
                pos.set(x, level.getMinY() + 1, z);
                if (!level.isLoaded(pos)) {
                    return Optional.empty();
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                pos.set(x, top - 1, z);
                BlockState surface = level.getBlockState(pos);
                boolean wet = surface.getFluidState().is(FluidTags.WATER);
                if (!wet && surface.is(BlockTags.ICE)) {
                    pos.set(x, top - 2, z);
                    wet = level.getBlockState(pos).getFluidState().is(FluidTags.WATER);
                }
                total++;
                if (wet) {
                    water++;
                }
            }
        }
        return total == 0 ? Optional.empty() : Optional.of(water * 100 / total);
    }
}
