package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.content.TownHallBlock;
import de.etiennebader.civ.server.town.TownHallService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Entfernt alte Gemeindehaus-Blöcke nach einer Verlegung oder Auflösung und gibt sie dem Spieler zurück.
 */
public final class TownHalls {

    private TownHalls() {
    }

    /** Führt die Folgen einer {@link TownHallService.SeatChange} in der Welt aus. */
    public static void apply(ServerPlayer player, TownHallService.SeatChange change) {
        Feedback.reply(player, change.reply());
        if (change.reply().success()) {
            change.oldSeat().ifPresent(seat -> remove(player.level().getServer(), seat, Optional.of(player)));
        }
    }

    /**
     * Entfernt das Gemeindehaus an dieser Stelle, falls dort eines steht und der Chunk geladen ist.
     *
     * @param refundTo erhält den Block als Item zurück
     */
    public static void remove(MinecraftServer server, BlockPosition position, Optional<ServerPlayer> refundTo) {
        Identifier dimension = Identifier.tryParse(position.dimension().value());
        if (dimension == null) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TownHallBlock)) {
            return;
        }
        level.removeBlock(pos, false);
        refundTo.ifPresent(player -> player.getInventory()
                .placeItemBackInInventory(new ItemStack(state.getBlock().asItem()), Prediction.SERVER_ONLY));
    }
}
