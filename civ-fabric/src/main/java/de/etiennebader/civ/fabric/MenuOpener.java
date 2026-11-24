package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.fabric.screen.BankScreen;
import de.etiennebader.civ.fabric.screen.ConstitutionScreen;
import de.etiennebader.civ.fabric.screen.LawBookScreen;
import de.etiennebader.civ.fabric.screen.MapScreen;
import de.etiennebader.civ.fabric.screen.MarketScreen;
import de.etiennebader.civ.fabric.screen.TownHallScreen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
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

    /** Das Menü eines Dorfbewohners: Auskunft und Handlungen (reden, beeinflussen, Post). */
    public static void villager(CivServer civ, ServerPlayer player, de.etiennebader.civ.domain.actor.PersonRef villager) {
        Feedback.click(player);
        Screens.open(player, new de.etiennebader.civ.fabric.screen.VillagerScreen(civ, player, villager));
    }

    /** Die Verfassung der Gemeinde, in deren Gebiet der Block steht; in der Wildnis ein Hinweis. */
    public static void constitution(CivServer civ, ServerPlayer player, BlockPosition position) {
        civ.constitutions().governing(position).ifPresentOrElse(
                polity -> {
                    Feedback.click(player);
                    Screens.open(player, new ConstitutionScreen(civ, player, polity, Optional.empty()));
                },
                () -> Feedback.reply(player, Reply.failure(Message.of(MessageKey.CONSTITUTION_NONE_HERE))));
    }

    /** Die Verfassung, deren Abschrift der Spieler liest. */
    public static void constitution(CivServer civ, ServerPlayer player, PolityId polity) {
        civ.queries().polity(polity).ifPresentOrElse(
                found -> {
                    Feedback.click(player);
                    Screens.open(player, new ConstitutionScreen(civ, player, found, Optional.empty()));
                },
                () -> Feedback.reply(player, Reply.failure(Message.of(MessageKey.POLITY_GONE))));
    }

    /** Das Gesetzbuch der Gemeinde, in deren Gebiet der Spieler steht; in der Wildnis das der eigenen Gemeinde. */
    public static void lawBook(CivServer civ, ServerPlayer player, BlockPosition position) {
        civ.constitutions().governing(position).or(() -> civ.queries().municipalityOf(Players.ref(player)))
                .ifPresentOrElse(polity -> {
                    Feedback.click(player);
                    Screens.open(player, new LawBookScreen(civ, player, polity, Optional.empty()));
                }, () -> Feedback.reply(player, Reply.failure(Message.of(MessageKey.CONSTITUTION_NONE_HERE))));
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
