package de.etiennebader.civ.fabric;

import de.etiennebader.civ.server.command.Reply;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Rückmeldung an einen Spieler: Nachricht plus passendes Geräusch, das nur er hört.
 */
public final class Feedback {

    private Feedback() {
    }

    /** Antwort in den Chat, dazu ein Erfolgs- oder Fehlergeräusch. */
    public static void reply(ServerPlayer player, Reply reply) {
        Components.send(player, reply);
        if (reply.success()) {
            success(player);
        } else {
            failure(player);
        }
    }

    /** Antwort auf einen Klick in einem Menü: erste Zeile über der Schnellzugriffsleiste, dazu ein Geräusch. */
    public static void inMenu(ServerPlayer player, Reply reply) {
        if (!reply.lines().isEmpty()) {
            Components.showHint(player, reply.lines().getFirst(), reply.success());
        }
        if (reply.success()) {
            success(player);
        } else {
            failure(player);
        }
    }

    public static void click(ServerPlayer player) {
        play(player, SoundEvents.UI_BUTTON_CLICK, 0.4F, 1.0F);
    }

    public static void success(ServerPlayer player) {
        play(player, SoundEvents.NOTE_BLOCK_CHIME, 0.6F, 1.5F);
    }

    public static void money(ServerPlayer player) {
        play(player, Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), 0.5F, 1.2F);
    }

    public static void failure(ServerPlayer player) {
        play(player, Holder.direct(SoundEvents.VILLAGER_NO), 0.6F, 1.0F);
    }

    public static void fanfare(ServerPlayer player) {
        play(player, Holder.direct(SoundEvents.PLAYER_LEVELUP), 0.6F, 1.0F);
    }

    public static void play(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, player.getX(), player.getY(),
                player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }
}
