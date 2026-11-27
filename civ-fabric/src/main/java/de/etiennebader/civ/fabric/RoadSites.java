package de.etiennebader.civ.fabric;

import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.road.RoadJob;
import de.etiennebader.civ.server.road.RoadService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Legt die Wege der Straßen (Wegebau) in die Welt: Mit der Schaufel wird der Boden zum Trampelpfad, beim Pflastern
 * entsteht ein Muster aus Bruchstein und Steinziegeln. Bewuchs über dem Weg verschwindet; Gebautes, Wasser und Lava
 * bleiben unberührt, die Straße läuft dort einfach weiter.
 */
final class RoadSites {

    private static final int INTERVAL = 40;
    private int ticks;

    void tick(MinecraftServer server, CivServer civ) {
        if (++ticks < INTERVAL) {
            return;
        }
        long elapsed = ticks;
        ticks = 0;
        for (RoadService.Work work : civ.roads().advance(elapsed)) {
            Identifier dimension = Identifier.tryParse(work.dimension().value());
            if (dimension == null) {
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) {
                continue;
            }
            for (int[] cell : work.cells()) {
                lay(level, cell[0], cell[1], work.material());
            }
        }
    }

    private static void lay(ServerLevel level, int x, int z, RoadJob.Material material) {
        BlockPos column = new BlockPos(x, 64, z);
        // Das Feld zählt schon als gelegt: Darum den Chunk laden, auch wenn niemand in der Nähe ist.
        if (!ChunkLoader.ensure(level, x, z, true)) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        BlockState state = level.getBlockState(ground);
        if (!state.getFluidState().isEmpty() || !soil(state)) {
            return;
        }
        // Gras, Blumen und Büsche über dem Weg räumen, bis zu zwei Blöcke hoch
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos above = ground.above(dy);
            BlockState plant = level.getBlockState(above);
            if (!plant.isAir() && (plant.canBeReplaced() || plant.is(BlockTags.FLOWERS) || plant.is(BlockTags.LEAVES))) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockState path = material == RoadJob.Material.PATH ? Blocks.DIRT_PATH.defaultBlockState()
                : (x + z) % 2 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        level.setBlock(ground, path, Block.UPDATE_ALL);
    }

    /** Nur natürlicher Boden wird zum Weg; ein Haus oder eine Mauer im Weg bleibt stehen. */
    private static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.SNOW_BLOCK);
    }
}
