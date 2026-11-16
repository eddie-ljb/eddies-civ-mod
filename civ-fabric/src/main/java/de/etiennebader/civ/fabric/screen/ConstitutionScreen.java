package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.content.ConstitutionCopies;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.constitution.ConstitutionView;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.town.LawSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Die Verfassung einer Gemeinde oder eines Bundes: Präambel, Verfassungsartikel, Organe, offene Änderungsanträge,
 * übergeordneter Bund und Glieder. Die Spitze ruft hier den Entwurf aus; jeder nimmt eine Abschrift mit.
 */
public final class ConstitutionScreen extends MenuScreen {

    private static final int[] ARTICLE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] CONSTITUENT_SLOTS = {39, 40, 41, 42, 43};
    private static final int LINE_LENGTH = 40;
    private static final int MAX_PREAMBLE_LINES = 8;

    private final PolityId polity;
    private final String initialName;

    public ConstitutionScreen(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
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
        return Components.of(GuiKeys.CONSTITUTION_TITLE.text(initialName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.blue()).hidden().build());
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.blue()).hidden().build());
        backButton(canvas);
        Optional<PolityView> found = civ.queries().polity(polity);
        if (found.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.POLITY_GONE), Icon.BAD).build());
            return;
        }
        ConstitutionView view = civ.constitutions().view(found.get(), Players.ref(player));
        canvas.set(4, book(view));
        renderArticles(canvas, view);
        renderOrgans(canvas, view);
        renderAmendments(canvas, view, found.get());
        renderProclaim(canvas, view);
        canvas.set(35, Icon.of(Items.PAPER).name(GuiKeys.CONSTITUTION_COPY.text(), Icon.TITLE)
                .hint(GuiKeys.CONSTITUTION_COPY_HINT.text()).build(), click -> {
            if (!click.player().getInventory().add(ConstitutionCopies.create(view.id(), view.name()))) {
                Feedback.failure(click.player());
                return;
            }
            report(click, Reply.success(Message.of(MessageKey.CONSTITUTION_COPY_TAKEN, view.name())));
        });
        renderLinks(canvas, view);
        canvas.set(27, Icon.of(Items.ENCHANTED_BOOK).name(GuiKeys.LAW_BOOK_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.LAW_BOOK_OPEN_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new LawBookScreen(civ, player, found.get(), Optional.of(this)));
        });
    }

    private static ItemStack book(ConstitutionView view) {
        Icon icon = Icon.of(Items.WRITTEN_BOOK).name(GuiKeys.CONSTITUTION_BOOK.text(view.name()), Icon.TITLE)
                .line(levelMessage(view), Icon.INFO)
                .line(view.proclaimedOn().map(day -> GuiKeys.CONSTITUTION_STATUS_PROCLAIMED.text(day))
                        .orElseGet(GuiKeys.CONSTITUTION_STATUS_DRAFT::text), view.proclaimed() ? Icon.GOOD : Icon.HINT)
                .line(GuiKeys.CONSTITUTION_MAJORITY.text(view.amendmentPercent()), Icon.INFO)
                .blank();
        if (view.preamble().isEmpty()) {
            icon.line(GuiKeys.CONSTITUTION_NO_PREAMBLE.text(), Icon.HINT);
        } else {
            for (String line : wrap(view.preamble())) {
                icon.line(GuiKeys.CONSTITUTION_PREAMBLE_LINE.text(line));
            }
        }
        if (view.head() && !view.proclaimed()) {
            icon.blank().hint(GuiKeys.CONSTITUTION_PREAMBLE_HINT.text());
        }
        return icon.build();
    }

    private static Message levelMessage(ConstitutionView view) {
        Object level = view.level();
        return level instanceof Message message ? message : GuiKeys.CONSTITUTION_PREAMBLE_LINE.text(level.toString());
    }

    private void renderArticles(Canvas canvas, ConstitutionView view) {
        List<LawSetting> articles = view.articles();
        for (int index = 0; index < Math.min(articles.size(), ARTICLE_SLOTS.length); index++) {
            LawSetting article = articles.get(index);
            Icon icon = Icon.of(article.isSwitch() ? (article.enabled() ? Items.DYE.lime() : Items.DYE.gray()) : Items.PAPER)
                    .name(article.name(), Icon.TITLE)
                    .line(GuiKeys.CONSTITUTION_ARTICLE_VALUE.text(civ.texts().lawValue(article.value())),
                            article.isSwitch() ? (article.enabled() ? Icon.GOOD : Icon.BAD) : Icon.INFO)
                    .blank()
                    .hint(view.proclaimed()
                            ? GuiKeys.CONSTITUTION_ARTICLE_HINT_VOTE.text(article.id(), view.amendmentPercent())
                            : GuiKeys.CONSTITUTION_ARTICLE_HINT_DRAFT.text(article.id()));
            if (!article.isSwitch()) {
                icon.count((int) Math.max(1, Math.min(64, article.number())));
            }
            canvas.set(ARTICLE_SLOTS[index], icon.build());
        }
    }

    private void renderOrgans(Canvas canvas, ConstitutionView view) {
        Icon icon = Icon.of(Items.BELL).name(GuiKeys.CONSTITUTION_ORGANS.text(), Icon.TITLE);
        for (OfficeView office : view.offices()) {
            icon.line(GuiKeys.CONSTITUTION_ORGAN_LINE.text(office.name(), office.holder().orElse("-")),
                    office.election().isPresent() ? Icon.GOOD : Icon.INFO);
        }
        canvas.set(29, icon.blank().hint(GuiKeys.CONSTITUTION_ORGANS_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new OfficesScreen(civ, player, view.id(), view.name(), this));
        });
    }

    private void renderAmendments(Canvas canvas, ConstitutionView view, PolityView polity) {
        int open = view.amendments().size();
        Icon icon = Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.CONSTITUTION_AMENDMENTS.text(open), Icon.TITLE)
                .count(Math.max(1, open));
        if (open > 0) {
            icon.glint();
        }
        canvas.set(31, icon.hint(GuiKeys.CONSTITUTION_AMENDMENTS_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new VotesScreen(civ, player, polity.id(), view.name(), this));
        });
    }

    private void renderProclaim(Canvas canvas, ConstitutionView view) {
        if (view.proclaimed()) {
            canvas.set(33, Icon.of(Items.GOLD_BLOCK).name(GuiKeys.CONSTITUTION_IN_FORCE.text(), Icon.GOOD)
                    .line(GuiKeys.CONSTITUTION_STATUS_PROCLAIMED.text(view.proclaimedOn().orElseThrow()))
                    .build());
            return;
        }
        Icon icon = Icon.of(Items.NETHER_STAR).name(GuiKeys.CONSTITUTION_PROCLAIM.text(), Icon.TITLE)
                .line(GuiKeys.CONSTITUTION_STATUS_DRAFT.text(), Icon.HINT)
                .line(GuiKeys.CONSTITUTION_PROCLAIM_LINE.text(), Icon.INFO);
        if (view.head()) {
            icon.blank().hint(GuiKeys.CONSTITUTION_PROCLAIM_HINT.text());
        }
        canvas.set(33, icon.build(), click -> {
            if (!view.head() || !click.shift()) {
                Feedback.click(click.player());
                return;
            }
            Reply reply = civ.constitutions().proclaim(caller(), view.id());
            report(click, reply);
            if (reply.success()) {
                Feedback.fanfare(click.player());
            }
        });
    }

    private void renderLinks(Canvas canvas, ConstitutionView view) {
        view.parent().ifPresent(parent -> canvas.set(37, Icon.of(Items.BEACON)
                .name(GuiKeys.CONSTITUTION_PARENT.text(parent.name()), Icon.TITLE)
                .hint(GuiKeys.CONSTITUTION_OPEN_HINT.text()).build(), click -> open(click.player(), parent)));
        List<ConstitutionView.Link> constituents = view.constituents();
        for (int index = 0; index < Math.min(constituents.size(), CONSTITUENT_SLOTS.length); index++) {
            ConstitutionView.Link constituent = constituents.get(index);
            canvas.set(CONSTITUENT_SLOTS[index], Icon.of(Items.LECTERN)
                    .name(GuiKeys.CONSTITUTION_CONSTITUENT.text(constituent.name()), Icon.TITLE)
                    .hint(GuiKeys.CONSTITUTION_OPEN_HINT.text()).build(), click -> open(click.player(), constituent));
        }
    }

    private void open(ServerPlayer clicker, ConstitutionView.Link link) {
        civ.queries().polity(link.id()).ifPresent(target -> {
            Feedback.click(clicker);
            Screens.open(clicker, new ConstitutionScreen(civ, player, target, Optional.of(this)));
        });
    }

    /** Bricht den Text an Wortgrenzen in Zeilen für den Tooltip um. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > LINE_LENGTH) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.size() > MAX_PREAMBLE_LINES) {
            List<String> cut = new ArrayList<>(lines.subList(0, MAX_PREAMBLE_LINES));
            cut.set(MAX_PREAMBLE_LINES - 1, cut.get(MAX_PREAMBLE_LINES - 1) + " …");
            return cut;
        }
        return lines;
    }
}
