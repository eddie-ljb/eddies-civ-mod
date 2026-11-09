package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.content.CivContent;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Reply;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Grundstücksstab: Linksklick auf einen Block setzt Ecke 1, Rechtsklick Ecke 2 – wie
 * {@code /civ grundstueck ecke1|ecke2}, nur an der angeklickten statt der eigenen Position.
 *
 * <p>Muss vor dem {@link ProtectionListener} registriert werden, damit der Stab keine Blöcke abbaut oder benutzt.
 */
public final class PlotWand {

    private final CivServerHost host;
    private final CivContent content;
    /** Letzte Ecke je Spieler und Nummer; Linksklicks feuern beim Gedrückthalten mehrfach. */
    private final Map<PlayerRef, Map<Integer, BlockPosition>> last = new HashMap<>();

    public PlotWand(CivServerHost host, CivContent content) {
        this.host = Objects.requireNonNull(host, "host");
        this.content = Objects.requireNonNull(content, "content");
    }

    public void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> click(player, level, hand, pos, 1));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> click(player, level, hand, hit.getBlockPos(), 2));
    }

    private InteractionResult click(Player player, Level level, InteractionHand hand, BlockPos pos, int corner) {
        if (!player.getItemInHand(hand).is(content.plotWand())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            return InteractionResult.SUCCESS;
        }
        BlockPosition position = Players.position(level, pos);
        PlayerRef ref = Players.ref(serverPlayer);
        Map<Integer, BlockPosition> corners = last.computeIfAbsent(ref, key -> new HashMap<>());
        if (position.equals(corners.get(corner))) {
            return InteractionResult.SUCCESS;
        }
        corners.put(corner, position);
        Caller caller = new Caller(ref, Players.name(serverPlayer), position);
        Reply reply = civ.get().plots().selectCorner(caller, corner);
        Feedback.reply(serverPlayer, reply);
        civ.get().plots().selection(ref).area().ifPresent(area -> host.showArea(serverPlayer, area));
        return InteractionResult.SUCCESS;
    }
}
