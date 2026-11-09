package de.etiennebader.civ.fabric;

import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Macht aus übersetzbaren {@link Message}s Minecraft-Texte. Übersetzt wird erst auf dem Client,
 * deshalb sieht jeder Spieler die Ausgabe in seiner eigenen Sprache.
 *
 * <p>Chatnachrichten bekommen ein einheitliches Präfix; Fehler sind rot, Erfolge hell.
 */
public final class Components {

    private static final int PREFIX_COLOR = 0xE0B04A;
    private static final int TEXT_COLOR = 0xE8E4D8;
    private static final int ERROR_COLOR = 0xFF6B5E;

    private Components() {
    }

    public static MutableComponent of(Message message) {
        Object[] args = message.args().stream()
                .map(arg -> arg instanceof Message nested ? of(nested) : arg)
                .toArray();
        return Component.translatable(message.key(), args);
    }

    /** Text für Gegenstände in Menüs: ohne die Kursivschrift, die Minecraft eigenen Namen sonst gibt. */
    public static MutableComponent plain(Message message, int color) {
        return of(message).withStyle(style -> style.withItalic(false).withColor(color));
    }

    public static MutableComponent plain(Message message, ChatFormatting color) {
        return of(message).withStyle(style -> style.withItalic(false).withColor(color));
    }

    /** {@code ⚑ } in Gold vor jeder Chatnachricht des Mods. */
    public static MutableComponent prefixed(Component text, boolean success) {
        return Component.literal("⚑ ").withStyle(style -> style.withColor(PREFIX_COLOR))
                .append(text.copy().withStyle(style -> style.withColor(success ? TEXT_COLOR : ERROR_COLOR)));
    }

    /** Schickt alle Zeilen einer Antwort als Chatnachrichten an einen Spieler. */
    public static void send(ServerPlayer player, Reply reply) {
        for (Message line : reply.lines()) {
            player.sendSystemMessage(prefixed(of(line), reply.success()));
        }
    }

    /** Zeigt einen kurzen Hinweis über der Schnellzugriffsleiste. */
    public static void showHint(ServerPlayer player, Message message, boolean success) {
        player.sendOverlayMessage(of(message).withStyle(style -> style.withColor(success ? TEXT_COLOR : ERROR_COLOR)));
    }

    /** Schickt eine Systemnachricht an alle Spieler. */
    public static void broadcast(MinecraftServer server, Message message) {
        server.getPlayerList().broadcastSystemMessage(prefixed(of(message), true), false);
    }
}
