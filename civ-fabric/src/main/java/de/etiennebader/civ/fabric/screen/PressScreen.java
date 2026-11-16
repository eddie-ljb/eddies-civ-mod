package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.content.Newspapers;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.press.ArticleView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Der Zeitungskiosk: die neuesten Artikel aller Zeitungen. Klick zeigt einen Artikel im Chat; unten gibt es die Ausgabe
 * als Buch.
 */
public final class PressScreen extends MenuScreen {

    private static final int SLOTS = 45;

    public PressScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.PRESS_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<ArticleView> articles = civ.press().latest(SLOTS);
        for (int index = 0; index < articles.size(); index++) {
            ArticleView article = articles.get(index);
            ChatFormatting color = article.tendency() > 0 ? ChatFormatting.GREEN
                    : article.tendency() < 0 ? ChatFormatting.RED : ChatFormatting.WHITE;
            Icon icon = Icon.of(article.tendency() == 0 ? Items.PAPER : Items.WRITTEN_BOOK)
                    .name(Icon.literal(article.title(), color))
                    .line(GuiKeys.PRESS_ARTICLE_LINE.text(article.newspaper(), article.day()), Icon.INFO);
            if (!article.subject().isEmpty()) {
                icon.line(GuiKeys.PRESS_STANCE.text(article.tendencyWord(), article.subject()), Icon.HINT);
            }
            String preview = article.body().replace('\n', ' ');
            icon.line(Icon.literal(preview.length() > 48 ? preview.substring(0, 47) + "…" : preview,
                    ChatFormatting.GRAY));
            canvas.set(index, icon.blank().hint(GuiKeys.PRESS_READ_HINT.text()).build(), click -> {
                Feedback.click(click.player());
                Feedback.reply(click.player(), civ.press().show(article.number()));
            });
        }
        if (articles.isEmpty()) {
            canvas.set(22, Icon.of(Items.PAPER).name(Message.of(MessageKey.EDITION_NONE), Icon.INFO).build());
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        canvas.set(49, Icon.of(Items.WRITTEN_BOOK).name(GuiKeys.PRESS_EDITION.text(), Icon.TITLE)
                .hint(GuiKeys.PRESS_EDITION_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    civ.press().edition(Optional.empty()).ifPresentOrElse(edition -> {
                        click.player().getInventory().placeItemBackInInventory(Newspapers.edition(edition), Prediction.SERVER_ONLY);
                        Feedback.success(click.player());
                    }, () -> Feedback.reply(click.player(), de.etiennebader.civ.server.command.Reply.failure(
                            Message.of(MessageKey.EDITION_NONE))));
                });
    }
}
