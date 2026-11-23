package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.religion.ReligionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/** Alle Religionen mit Logo, Gläubigen und Klerus (Issue #13). Klick tritt bei, Schleichen und Klick tritt aus. */
public final class ReligionScreen extends MenuScreen {

    private static final int SLOTS = 45;

    public ReligionScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.RELIGION_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<ReligionService.ReligionEntry> entries = civ.religion().entries(Players.ref(player));
        for (int index = 0; index < Math.min(SLOTS, entries.size()); index++) {
            ReligionService.ReligionEntry entry = entries.get(index);
            String name = (entry.logo().isEmpty() ? "" : "[" + entry.logo() + "] ") + entry.religion().name();
            Icon icon = Icon.of(entry.mine() ? Items.ENCHANTED_BOOK : Items.BOOK)
                    .name(Message.of(MessageKey.RELIGION_DONE, name), Icon.TITLE)
                    .line(GuiKeys.RELIGION_STATS.text(entry.members(), entry.head()), Icon.INFO)
                    .line(Message.of(MessageKey.RELIGION_DONE, entry.religion().templeName() + " · "
                            + entry.religion().scriptureName()), Icon.HINT);
            if (entry.mine()) {
                icon.glint().line(GuiKeys.RELIGION_MEMBER.text(), Icon.GOOD).hint(GuiKeys.RELIGION_LEAVE_HINT.text());
            } else {
                icon.hint(GuiKeys.RELIGION_JOIN_HINT.text());
            }
            canvas.set(index, icon.build(), click -> {
                Feedback.click(click.player());
                report(click, click.shift() && entry.mine()
                        ? civ.religion().leave(Players.caller(click.player()))
                        : civ.religion().join(Players.caller(click.player()), entry.religion().name()));
            });
        }
        if (entries.isEmpty()) {
            canvas.set(22, Icon.of(Items.BOOK).name(GuiKeys.RELIGION_NONE.text(), Icon.INFO).build());
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
    }
}
