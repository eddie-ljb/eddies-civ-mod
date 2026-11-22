package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.civic.HomePortService;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;

/**
 * Der Teleport zu den Heimat-Häfen einer Polity ({@code /civ heim}). Recht und Preis klärt der
 * {@link HomePortService}; hier steht nur, was Minecraft kennt: die Zielwelt und ein sicherer Landeplatz.
 * Das Geld wird erst gebucht, wenn ein Landeplatz gefunden ist, damit niemand für einen verschütteten Hafen zahlt.
 */
final class HomePorts {

    /** Wie weit um das Ziel herum nach einem freien Platz gesucht wird. */
    private static final int SEARCH_RADIUS = 3;

    private HomePorts() {
    }

    static Reply travel(CivServer civ, ServerPlayer player, Optional<String> input) {
        HomePortService.Outcome outcome = civ.homePorts().prepare(Players.caller(player), input);
        if (outcome.trip().isEmpty()) {
            return outcome.refusal().orElseGet(() -> Reply.failure(Message.of(MessageKey.INTERNAL_ERROR)));
        }
        HomePortService.Trip trip = outcome.trip().get();
        MinecraftServer server = player.level().getServer();
        Optional<ServerLevel> level = levelOf(server, trip.target());
        Optional<BlockPos> landing = level.flatMap(world -> safeSpot(world, trip.target()));
        if (landing.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PORT_NO_SAFE_SPOT));
        }
        Reply paid = civ.homePorts().pay(Players.caller(player), trip);
        if (!paid.success()) {
            return paid;
        }
        BlockPos spot = landing.get();
        player.teleportTo(level.get(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(),
                player.getYRot(), player.getXRot(), true);
        return paid;
    }

    private static Optional<ServerLevel> levelOf(MinecraftServer server, BlockPosition position) {
        Identifier dimension = Identifier.tryParse(position.dimension().value());
        return dimension == null ? Optional.empty()
                : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)));
    }

    /**
     * Ein Platz mit festem Boden, freien Füßen und freiem Kopf ohne Flüssigkeit, möglichst nah am Ziel.
     * Der Hafen liegt oft auf dem Gemeindehaus-Block selbst, deshalb wird die Umgebung abgesucht.
     */
    private static Optional<BlockPos> safeSpot(ServerLevel level, BlockPosition target) {
        level.getChunk(target.x() >> 4, target.z() >> 4);
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int step = 0; step <= 2 * SEARCH_RADIUS; step++) {
                        int dy = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
                        BlockPos feet = new BlockPos(target.x() + dx, target.y() + dy, target.z() + dz);
                        if (feet.getY() > level.getMinY() && standable(level, feet)) {
                            return Optional.of(feet);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return floor.isSolid() && floor.getFluidState().isEmpty()
                && body.isAir() && head.isAir();
    }
}
