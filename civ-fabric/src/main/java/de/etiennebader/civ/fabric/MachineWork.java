package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.production.MachinePos;
import de.etiennebader.civ.fabric.content.MachineBlock;
import de.etiennebader.civ.server.CivServer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Verbindet die Maschinen mit der Welt: abgebaute Blöcke melden sich ab, verschwundene Blöcke (Explosionen u. Ä.)
 * werden erkannt, und Angestellte gehen tagsüber zu den Maschinen ihrer Firma.
 */
public final class MachineWork {

    private static final int ARRIVED_DISTANCE_SQ = 16;
    private static final double WALK_SPEED = 0.6;

    private final CivServerHost host;

    public MachineWork(CivServerHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof MachineBlock) {
                host.current().ifPresent(civ -> civ.machines().removed(position(level, pos)));
            }
        });
    }

    public static MachinePos position(Level level, BlockPos pos) {
        return new MachinePos(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** Entfernt Maschinen, deren Block nicht mehr steht (nur in geladenen Chunks). */
    static void verify(MinecraftServer server, CivServer civ) {
        for (MachinePos position : civ.machines().placedPositions()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(position.dimension())));
            if (level == null) {
                continue;
            }
            BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof MachineBlock)) {
                civ.machines().removed(position);
            }
        }
    }

    /** Schickt einen angestellten Dorfbewohner tagsüber zu den Maschinen seiner Firma. */
    static void commute(CivServer civ, ServerLevel level, Villager villager, PersonRef ref) {
        if (villager.isBaby() || !level.isBrightOutside()) {
            return;
        }
        Optional<de.etiennebader.civ.server.machine.MachineService.Workplace> workplace =
                civ.machines().workplaceOf(ref);
        if (workplace.isEmpty()) {
            return;
        }
        String dimension = level.dimension().identifier().toString();
        Optional<BlockPos> target = workplace.get().machines().stream()
                .filter(position -> position.dimension().equals(dimension))
                .map(position -> new BlockPos(position.x(), position.y(), position.z()))
                .min(Comparator.comparingDouble(pos -> villager.blockPosition().distSqr(pos)));
        if (target.isEmpty()) {
            return;
        }
        if (villager.blockPosition().distSqr(target.get()) <= ARRIVED_DISTANCE_SQ) {
            civ.machines().worked(workplace.get().company(), ref);
        } else {
            villager.getNavigation().moveTo(target.get().getX() + 0.5, target.get().getY(),
                    target.get().getZ() + 0.5, WALK_SPEED);
        }
    }
}
