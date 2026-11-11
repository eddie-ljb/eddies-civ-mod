package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.TranslationKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Wahlprogramm wählen, bevor man kandidiert. Ein Klick kandidiert und führt zurück zu den Ämtern.
 */
final class PlatformScreen extends MenuScreen {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    private final Office office;
    private final Screen parent;

    PlatformScreen(CivServer civ, ServerPlayer player, Office office, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.office = office;
        this.parent = parent;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.PLATFORM_TITLE.text(Message.of(TranslationKeys.office(office))));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(2, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        int index = 0;
        for (Platform platform : Platform.values()) {
            canvas.set(SLOTS[index++], Icon.of(item(platform))
                    .name(Message.of(TranslationKeys.platform(platform)), Icon.TITLE)
                    .line(Message.of(TranslationKeys.platformDescription(platform)), Icon.INFO)
                    .blank().hint(GuiKeys.PLATFORM_HINT.text()).build(), click -> {
                Reply reply = civ.offices().stand(caller(), office, platform);
                report(click, reply);
                if (reply.success()) {
                    Screens.open(click.player(), parent);
                }
            });
        }
    }

    private static Item item(Platform platform) {
        return switch (platform) {
            case NONE -> Items.PAPER;
            case ORDER -> Items.IRON_BARS;
            case FREEDOM -> Items.FEATHER;
            case LOW_TAXES -> Items.GOLD_NUGGET;
            case INVESTMENT -> Items.BRICKS;
        };
    }
}
