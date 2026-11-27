package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.content.LawBooks;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.law.ParagraphView;
import de.etiennebader.civ.server.town.LawSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Das Gesetzbuch einer Gemeinde oder eines Bundes: alle Paragraphen mit Text und erkannten Regelungen.
 * Umschalt-Klick auf einen Paragraphen hebt ihn auf (oder beantragt das); mit Buch und Feder in der Hand wird
 * ein neuer Paragraph erlassen (oder beantragt).
 */
public final class LawBookScreen extends MenuScreen {

    private static final int FIRST_SLOT = 9;
    private static final int LAST_SLOT = 44;
    private static final int MAX_TEXT_LINES = 6;

    private final PolityId polity;
    private final String initialName;

    public LawBookScreen(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
        super(civ, player, parent);
        this.polity = polity.id();
        this.initialName = polity.name().value();
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.LAW_BOOK_TITLE.text(initialName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.brown()).hidden().build());
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.brown()).hidden().build());
        backButton(canvas);
        Optional<PolityView> found = civ.queries().polity(polity);
        if (found.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.POLITY_GONE), Icon.BAD).build());
            return;
        }
        PolityView view = found.get();
        List<ParagraphView> paragraphs = civ.lawBooks().paragraphsOf(view);
        canvas.set(4, info(view, paragraphs.size()));

        if (paragraphs.isEmpty()) {
            canvas.set(22, Icon.of(Items.BOOK).name(GuiKeys.LAW_BOOK_EMPTY.text(), Icon.INFO).build());
        }
        int slot = FIRST_SLOT;
        for (ParagraphView paragraph : paragraphs) {
            if (slot > LAST_SLOT) {
                break;
            }
            canvas.set(slot++, icon(paragraph), click -> {
                if (!click.shift()) {
                    Feedback.click(click.player());
                    return;
                }
                report(click, civ.lawBooks().repeal(caller(), polity, paragraph.number()));
            });
        }

        canvas.set(47, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.LAW_BOOK_WRITE.text(), Icon.TITLE)
                .line(GuiKeys.LAW_BOOK_WRITE_LINE.text(), Icon.INFO)
                .hint(GuiKeys.LAW_BOOK_WRITE_HINT.text()).build(), click -> {
            Optional<LawBooks.Draft> draft = LawBooks.draftOf(click.player().getMainHandItem());
            if (draft.isEmpty()) {
                report(click, Reply.failure(Message.of(MessageKey.PARAGRAPH_NEEDS_TEXT)));
                return;
            }
            report(click, civ.lawBooks().enact(caller(), polity, draft.get().title(), draft.get().text()));
        });

        int open = view.proposals().size();
        canvas.set(49, Icon.of(Items.PAPER).name(GuiKeys.LAW_BOOK_PROPOSALS.text(open), Icon.TITLE)
                .count(Math.max(1, open)).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new VotesScreen(civ, player, polity, view.name().value(), this));
        });

        civ.queries().parentOf(polity).ifPresent(parent -> canvas.set(51, Icon.of(Items.BEACON)
                .name(GuiKeys.LAW_BOOK_PARENT.text(parent.name().value()), Icon.TITLE)
                .hint(GuiKeys.CONSTITUTION_OPEN_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new LawBookScreen(civ, player, parent, Optional.of(this)));
        }));
    }

    private ItemStack info(PolityView view, int count) {
        Icon icon = Icon.of(Items.ENCHANTED_BOOK).name(GuiKeys.LAW_BOOK_INFO.text(view.name().value()), Icon.TITLE)
                .line(GuiKeys.LAW_BOOK_COUNT.text(count), Icon.INFO);
        List<String> overriding = civ.queries().ancestorsOf(view.id()).stream()
                .filter(federation -> federation.laws().valueOf(StandardParameters.FEDERAL_LAW_PREVAILS))
                .map(federation -> federation.name().value())
                .toList();
        icon.line(overriding.isEmpty() ? GuiKeys.LAW_BOOK_OWN_LINE.text()
                : GuiKeys.LAW_BOOK_FEDERAL_LINE.text(String.join(", ", overriding)), Icon.HINT);
        return icon.build();
    }

    private ItemStack icon(ParagraphView paragraph) {
        Icon icon = Icon.of(paragraph.effects().isEmpty() ? Items.BOOK : Items.WRITTEN_BOOK)
                .name(GuiKeys.PARAGRAPH_NAME.text(paragraph.number(), paragraph.qualifiedTitle()), Icon.TITLE)
                .line(GuiKeys.PARAGRAPH_ENACTED_ON.text(paragraph.enactedOn()), Icon.HINT);
        for (LawSetting effect : paragraph.effects()) {
            icon.line(GuiKeys.PARAGRAPH_EFFECT.text(effect.name(), civ.texts().lawValue(effect.value())), Icon.GOOD);
        }
        if (!paragraph.text().isEmpty()) {
            icon.blank();
            List<String> lines = ConstitutionScreen.wrap(paragraph.text());
            for (String line : lines.subList(0, Math.min(lines.size(), MAX_TEXT_LINES))) {
                icon.line(GuiKeys.PARAGRAPH_TEXT_LINE.text(line));
            }
        }
        if (!paragraph.unrecognized().isEmpty()) {
            icon.line(GuiKeys.PARAGRAPH_UNENFORCED.text(), Icon.BAD);
        }
        return icon.blank().hint(GuiKeys.PARAGRAPH_REPEAL_HINT.text()).build();
    }
}
