package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.territory.Relation;
import de.etiennebader.civ.server.territory.TerritoryChange;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Zeigt beim Überschreiten einer Gebietsgrenze den Namen des neuen Gebiets als Titel an.
 */
final class TerritoryNotifier {

    /** Zweimal pro Sekunde prüfen genügt. */
    private static final int INTERVAL = 10;

    private static final int OWN_COLOR = 0x7BD88F;
    private static final int FOREIGN_COLOR = 0xF2C14E;
    private static final int WILDERNESS_COLOR = 0x9FB8A0;

    private int ticks;

    void tick(MinecraftServer server, CivServer civ) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkCoord chunk = chunkOf(player);
            civ.territory().moved(Players.ref(player), chunk).ifPresent(change -> show(player, change));
            civ.plots().moved(Players.ref(player), Players.position(player))
                    .ifPresent(message -> Components.showHint(player, message, true));
        }
    }

    static ChunkCoord chunkOf(ServerPlayer player) {
        DimensionId dimension = new DimensionId(player.level().dimension().identifier().toString());
        return new ChunkCoord(dimension, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
    }

    private static void show(ServerPlayer player, TerritoryChange change) {
        int color = switch (change.relation()) {
            case OWN -> OWN_COLOR;
            case FOREIGN -> FOREIGN_COLOR;
            case WILDERNESS -> WILDERNESS_COLOR;
        };
        Component title = Components.of(change.title()).withStyle(style -> style.withColor(color));
        Component subtitle = Components.of(change.subtitle()).withStyle(style -> style.withColor(0xD0D0D0));
        int stay = change.relation() == Relation.WILDERNESS ? 30 : 50;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, stay, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
