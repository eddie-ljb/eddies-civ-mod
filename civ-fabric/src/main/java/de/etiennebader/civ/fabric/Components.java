package de.etiennebader.civ.fabric;

import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Macht aus übersetzbaren {@link Message}s Minecraft-Texte. Übersetzt wird erst auf dem Client,
 * deshalb sieht jeder Spieler die Ausgabe in seiner eigenen Sprache.
 */
final class Components {

    private Components() {
    }

    static MutableComponent of(Message message) {
        Object[] args = message.args().stream()
                .map(arg -> arg instanceof Message nested ? of(nested) : arg)
                .toArray();
        return Component.translatable(message.key(), args);
    }

    /** Schickt alle Zeilen einer Antwort als Chatnachrichten an einen Spieler. */
    static void send(ServerPlayer player, Reply reply) {
        for (Message line : reply.lines()) {
            player.sendSystemMessage(of(line));
        }
    }

    /** Zeigt einen kurzen Hinweis über der Schnellzugriffsleiste. */
    static void showHint(ServerPlayer player, Message message) {
        player.sendOverlayMessage(of(message));
    }

    /** Schickt eine Systemnachricht an alle Spieler. */
    static void broadcast(MinecraftServer server, Message message) {
        server.getPlayerList().broadcastSystemMessage(of(message), false);
    }
}
