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
import de.etiennebader.civ.server.community.FriendView;
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
 * Freunde und Anfragen. Klick auf eine Anfrage nimmt sie an, Schleichen und Klick entfernt einen Freund oder zieht
 * eine Anfrage zurück. Neue Freunde fragt man mit {@code /civ freund <Spieler>} oder auf der Webseite an.
 */
public final class FriendsScreen extends MenuScreen {

    private static final int SLOTS = 45;

    public FriendsScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.FRIENDS_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<FriendView> friends = civ.community().friends(Players.ref(player));
        for (int index = 0; index < Math.min(SLOTS, friends.size()); index++) {
            FriendView friend = friends.get(index);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(friend.name()));
            Icon icon = Icon.of(head);
            switch (friend.state()) {
                case "incoming" -> icon.name(Icon.literal(friend.name(), ChatFormatting.YELLOW))
                        .line(GuiKeys.FRIEND_INCOMING.text(), Icon.TITLE)
                        .blank().hint(GuiKeys.FRIEND_ACCEPT_HINT.text()).hint(GuiKeys.FRIEND_REMOVE_HINT.text());
                case "outgoing" -> icon.name(Icon.literal(friend.name(), ChatFormatting.GRAY))
                        .line(GuiKeys.FRIEND_OUTGOING.text(), Icon.HINT)
                        .blank().hint(GuiKeys.FRIEND_REMOVE_HINT.text());
                default -> {
                    icon.name(Icon.literal(friend.name(), friend.online() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                            .line(friend.online() ? GuiKeys.FRIEND_ONLINE.text() : GuiKeys.FRIEND_OFFLINE.text(),
                                    friend.online() ? Icon.GOOD : Icon.HINT);
                    if (friend.online()) {
                        icon.glint();
                    }
                    icon.blank().hint(GuiKeys.FRIEND_REMOVE_HINT.text());
                }
            }
            if (!friend.town().isEmpty()) {
                icon.line(GuiKeys.FRIEND_TOWN.text(friend.town()), Icon.INFO);
            }
            canvas.set(index, icon.build(), click -> {
                Feedback.click(click.player());
                if (click.shift()) {
                    report(click, civ.community().unfriend(Players.ref(click.player()), friend.name()));
                } else if (friend.state().equals("incoming")) {
                    report(click, civ.community().befriend(Players.ref(click.player()), Players.name(click.player()),
                            friend.name()));
                }
            });
        }
        if (friends.isEmpty()) {
            canvas.set(22, Icon.of(Items.BOOK).name(Message.of(MessageKey.FRIENDS_NONE), Icon.INFO).build());
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        canvas.set(49, Icon.of(Items.KNOWLEDGE_BOOK).name(GuiKeys.FRIENDS_INFO.text(), Icon.TITLE)
                .line(GuiKeys.FRIENDS_INFO_LINE.text(), Icon.INFO)
                .line(GuiKeys.FRIENDS_ONLINE_COUNT.text(civ.community().onlineCount()), Icon.HINT).build());
    }
}
