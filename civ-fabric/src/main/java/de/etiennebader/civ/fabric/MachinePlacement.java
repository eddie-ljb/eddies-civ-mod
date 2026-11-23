package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.production.MachineType;
import de.etiennebader.civ.fabric.content.MachineBlock;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

/** Meldet aufgestellte Maschinenblöcke der Firmenverwaltung. */
public final class MachinePlacement implements MachineBlock.PlacementHandler {

    private final CivServerHost host;

    public MachinePlacement(CivServerHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public boolean placed(ServerPlayer player, BlockPos pos, MachineType type) {
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            return false;
        }
        boolean ok = civ.get().machines().placed(Players.ref(player), type, MachineWork.position(player.level(), pos));
        Components.send(player, ok ? Reply.success(Message.of(MessageKey.MACHINE_PLACED))
                : Reply.failure(Message.of(MessageKey.MACHINE_PLACE_REFUSED)));
        return ok;
    }
}
