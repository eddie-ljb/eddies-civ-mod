package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.community.Letter;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.content.LawBooks;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Click;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.community.FriendView;
import de.etiennebader.civ.server.community.LetterView;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;

/**
 * Der Briefkasten: oben das Postfach, darunter die Freunde, an die man einen Brief schreibt. Der Brief ist ein Buch
 * mit Feder in der Hand; die erste Zeile ist der Betreff. Unten Briefwahl, Freunde und Verträge.
 */
public final class MailScreen extends MenuScreen {

    private static final int LETTER_SLOTS = 27;
    private static final int FRIEND_ROW = 4;

    public MailScreen(CivServer civ, ServerPlayer player) {
        this(civ, player, Optional.empty());
    }

    public MailScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.MAIL_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<LetterView> letters = civ.community().inbox(Players.ref(player));
        for (int index = 0; index < Math.min(LETTER_SLOTS, letters.size()); index++) {
            LetterView letter = letters.get(index);
            String preview = letter.body().replace('\n', ' ');
            Icon icon = Icon.of(letter.read() ? Items.PAPER : Items.WRITABLE_BOOK)
                    .name(Icon.literal(letter.subject(), letter.read()
                            ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.YELLOW))
                    .line(GuiKeys.MAIL_FROM.text(letter.from(), letter.sentOn()), Icon.INFO)
                    .line(GuiKeys.MAIL_PREVIEW.text(preview.length() > 40 ? preview.substring(0, 39) + "…" : preview),
                            Icon.HINT);
            if (!letter.read()) {
                icon.line(GuiKeys.MAIL_NEW.text(), Icon.GOOD).glint();
            }
            canvas.set(index, icon.blank().hint(GuiKeys.MAIL_READ_HINT.text()).build(), click -> {
                Feedback.click(click.player());
                Feedback.reply(click.player(), civ.community().readLetter(caller(), letter.number()));
            });
        }
        if (letters.isEmpty()) {
            canvas.set(13, Icon.of(Items.PAPER).name(Message.of(MessageKey.MAIL_EMPTY), Icon.INFO).build());
        }

        canvas.fillRow(3, Icon.of(Items.STAINED_GLASS_PANE.yellow()).name(GuiKeys.MAIL_WRITE.text(), Icon.TITLE)
                .line(GuiKeys.MAIL_WRITE_LINE.text(), Icon.INFO).build());
        List<FriendView> friends = civ.community().friends(Players.ref(player)).stream()
                .filter(friend -> friend.state().equals("friend")).toList();
        for (int index = 0; index < Math.min(9, friends.size()); index++) {
            FriendView friend = friends.get(index);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(friend.name()));
            canvas.set(FRIEND_ROW * 9 + index, Icon.of(head).name(GuiKeys.MAIL_TO.text(friend.name()), Icon.TITLE)
                    .line(friend.online() ? GuiKeys.FRIEND_ONLINE.text() : GuiKeys.FRIEND_OFFLINE.text(),
                            friend.online() ? Icon.GOOD : Icon.HINT)
                    .blank().hint(GuiKeys.MAIL_SEND_HINT.text()).build(), click -> send(click, friend.name()));
        }
        if (friends.isEmpty()) {
            canvas.set(FRIEND_ROW * 9 + 4, Icon.of(Items.BOOK).name(Message.of(MessageKey.FRIENDS_NONE), Icon.INFO)
                    .line(GuiKeys.MAIL_ANYONE_LINE.text(), Icon.HINT).build());
        }

        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        canvas.set(47, Icon.of(Items.OAK_SIGN).name(GuiKeys.MAIL_ADDRESS.text(), Icon.TITLE)
                .line(Icon.literal(civ.civil().residenceLine(Players.ref(player)).orElse("–"),
                        net.minecraft.ChatFormatting.GRAY)).build());
        if (!civ.campaigns().postalPolitiesOf(Players.ref(player)).isEmpty()) {
            canvas.set(49, Icon.of(Items.WRITTEN_BOOK).name(GuiKeys.POSTAL_VOTE_TITLE.text(), Icon.TITLE)
                    .hint(GuiKeys.POSTAL_VOTE_HINT.text()).build(), click -> {
                        Feedback.click(click.player());
                        Screens.open(click.player(), BallotBoxScreen.postal(civ, click.player()));
                    });
        } else {
            canvas.set(49, Icon.of(Items.DYE.gray()).name(Message.of(MessageKey.POSTAL_VOTE_CLOSED), Icon.HINT).build());
        }
        canvas.set(51, Icon.of(Items.PLAYER_HEAD).name(GuiKeys.FRIENDS_OPEN.text(), Icon.TITLE).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new FriendsScreen(civ, click.player(), Optional.of(this)));
        });
        canvas.set(53, Icon.of(Items.FILLED_MAP).name(GuiKeys.CONTRACTS_OPEN.text(), Icon.TITLE).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new ContractsScreen(civ, click.player(), Optional.of(this)));
        });
    }

    /** Sendet das Buch mit Feder in der Hand als Brief; das Buch wird dabei eingeworfen. */
    private void send(Click click, String recipient) {
        ItemStack stack = click.player().getItemInHand(InteractionHand.MAIN_HAND);
        Optional<LawBooks.Draft> draft = LawBooks.draftOf(stack);
        if (draft.isEmpty()) {
            report(click, Reply.failure(Message.of(MessageKey.LETTER_FROM_BOOK)));
            return;
        }
        String subject = draft.get().title();
        if (subject.length() > Letter.MAX_SUBJECT) {
            subject = subject.substring(0, Letter.MAX_SUBJECT - 1).strip() + "…";
        }
        String body = draft.get().text().equals(draft.get().title()) ? "" : draft.get().text();
        Reply reply = civ.community().sendLetter(Players.ref(click.player()), Players.name(click.player()), recipient,
                subject, body);
        if (reply.success()) {
            stack.shrink(1);
            Feedback.success(click.player());
        }
        report(click, reply);
    }
}
