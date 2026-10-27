package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.command.Caller;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Übersetzt Minecraft-Spieler und -Positionen in Begriffe der Zivilisation.
 */
final class Players {

    private Players() {
    }

    static PlayerRef ref(ServerPlayer player) {
        return new PlayerRef(new PlayerId(player.getUUID()));
    }

    static String name(ServerPlayer player) {
        return player.getScoreboardName();
    }

    static BlockPosition position(Level level, BlockPos pos) {
        DimensionId dimension = new DimensionId(level.dimension().identifier().toString());
        return new BlockPosition(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Der Block, auf dem der Spieler steht. */
    static BlockPosition position(ServerPlayer player) {
        return position(player.level(), player.blockPosition());
    }

    static Caller caller(ServerPlayer player) {
        return new Caller(ref(player), name(player), position(player));
    }
}
