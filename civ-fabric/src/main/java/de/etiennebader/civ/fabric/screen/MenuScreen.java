package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Click;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.Optional;

/**
 * Gemeinsame Grundlage der Menüs des Mods: Zugriff auf die Zivilisation, Zurück-Knopf und Rückmeldungen.
 */
abstract class MenuScreen extends Screen {

    protected final CivServer civ;
    protected final ServerPlayer player;
    private final Optional<Screen> parent;

    MenuScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        this.civ = Objects.requireNonNull(civ, "civ");
        this.player = Objects.requireNonNull(player, "player");
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    protected Caller caller() {
        return Players.caller(player);
    }

    protected Message money(Money amount) {
        return civ.texts().money(amount);
    }

    /** Setzt unten links einen Knopf, der zum vorigen Menü zurückkehrt oder das Menü schließt. */
    protected void backButton(Canvas canvas) {
        int slot = (rows() - 1) * 9;
        canvas.set(slot, Icon.of(parent.isPresent() ? Items.ARROW : Items.BARRIER).name(GuiKeys.BACK.text()).build(),
                click -> {
                    Feedback.click(click.player());
                    parent.ifPresentOrElse(screen -> Screens.open(click.player(), screen),
                            () -> Screens.close(click.player()));
                });
    }

    /** Führt eine Aktion aus und meldet das Ergebnis über der Schnellzugriffsleiste. */
    protected static void report(Click click, Reply reply) {
        Feedback.inMenu(click.player(), reply);
    }
}
