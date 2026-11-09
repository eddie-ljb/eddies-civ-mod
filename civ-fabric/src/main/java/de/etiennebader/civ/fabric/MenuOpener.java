package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.fabric.screen.BankScreen;
import de.etiennebader.civ.fabric.screen.MapScreen;
import de.etiennebader.civ.fabric.screen.MarketScreen;
import de.etiennebader.civ.fabric.screen.TownHallScreen;
import de.etiennebader.civ.server.CivServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Öffnet die Menüs des Mods für einen Spieler.
 */
public final class MenuOpener {

    private MenuOpener() {
    }

    /** Das Menü des Gemeindehauses an dieser Stelle; ohne Gemeinde ein Hinweis im Chat. */
    public static void townHall(CivServer civ, ServerPlayer player, BlockPosition seat) {
        civ.towns().townWithSeat(seat, Players.ref(player)).ifPresentOrElse(
                town -> {
                    Feedback.click(player);
                    Screens.open(player, new TownHallScreen(civ, player, town));
                },
                () -> Feedback.reply(player, civ.commands().describeTownHall(seat)));
    }

    public static void bank(CivServer civ, ServerPlayer player) {
        Feedback.click(player);
        Screens.open(player, new BankScreen(civ, player));
    }

    public static void market(CivServer civ, ServerPlayer player) {
        Feedback.click(player);
        Screens.open(player, new MarketScreen(civ, player));
    }

    public static void map(CivServer civ, ServerPlayer player) {
        Feedback.click(player);
        Screens.open(player, new MapScreen(civ, player, Optional.empty()));
    }

    /** Das Menü der eigenen Gemeinde, egal wo der Spieler steht. */
    public static boolean ownTown(CivServer civ, ServerPlayer player) {
        return civ.towns().ownTown(Players.ref(player)).map(town -> {
            Feedback.click(player);
            Screens.open(player, new TownHallScreen(civ, player, town));
            return true;
        }).orElse(false);
    }
}
