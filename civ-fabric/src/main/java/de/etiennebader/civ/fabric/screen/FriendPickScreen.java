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
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;

/**
 * Online-Spieler, die noch keine Freunde sind: Klick schickt eine Freundschaftsanfrage.
 */
public final class FriendPickScreen extends MenuScreen {

    private static final int SLOTS = 45;

    public FriendPickScreen(CivServer civ, ServerPlayer player, Screen parent) {
        super(civ, player, Optional.of(parent));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.FRIEND_ADD.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<String> names = civ.community().strangersOnline(Players.ref(player));
        for (int index = 0; index < Math.min(SLOTS, names.size()); index++) {
            String name = names.get(index);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
            canvas.set(index, Icon.of(head).name(Icon.literal(name, ChatFormatting.WHITE))
                    .hint(GuiKeys.FRIEND_REQUEST_HINT.text()).build(), click -> {
                        Feedback.click(click.player());
                        report(click, civ.community().befriend(Players.ref(click.player()), Players.name(click.player()),
                                name));
                    });
        }
        if (names.isEmpty()) {
            canvas.set(22, Icon.of(Items.BOOK).name(Message.of(MessageKey.FRIEND_NOBODY_ONLINE), Icon.INFO).build());
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
    }
}
