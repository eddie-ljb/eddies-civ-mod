package de.etiennebader.civ.fabric;

import de.etiennebader.civ.fabric.content.TownHallBlock;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Gründet beim Setzen eines Gemeindehauses die Gemeinde: Name aus dem Amboss, Sitz an der Stelle des Blocks.
 */
public final class TownHallPlacement implements TownHallBlock.PlacementHandler {

    private final CivServerHost host;

    public TownHallPlacement(CivServerHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public boolean placed(ServerPlayer player, BlockPos pos, ItemStack stack) {
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            Components.send(player, Reply.failure(Message.of(MessageKey.INTERNAL_ERROR)));
            return false;
        }
        Optional<String> name = Optional.ofNullable(stack.get(DataComponents.CUSTOM_NAME)).map(Component::getString);
        Reply reply = civ.get().commands().foundWithTownHall(Players.caller(player), name,
                Players.position(player.level(), pos));
        Components.send(player, reply);
        return reply.success();
    }
}
