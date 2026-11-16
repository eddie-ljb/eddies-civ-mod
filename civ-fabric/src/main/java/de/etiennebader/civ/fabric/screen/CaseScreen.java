package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.justice.Stage;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
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
import de.etiennebader.civ.server.justice.CaseView;
import de.etiennebader.civ.server.justice.JusticeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Die Akte eines Verfahrens: Beteiligte, Beweise, Aussagen, Urteil und die Handlungen, die dem Betrachter offenstehen.
 */
public final class CaseScreen extends MenuScreen {

    private static final int FIRST_EVIDENCE = 9;
    private static final int EVIDENCE_SLOTS = 9;
    private static final int FIRST_STATEMENT = 18;
    private static final int STATEMENT_SLOTS = 18;
    private static final int TEXT_WIDTH = 48;

    private final int number;

    public CaseScreen(CivServer civ, ServerPlayer player, int number, Optional<Screen> parent) {
        super(civ, player, parent);
        this.number = number;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.CASE_TITLE.text(number));
    }

    @Override
    public void render(Canvas canvas) {
        Optional<CaseView> found = civ.justice().caseView(number, Optional.of(Players.ref(player)));
        if (found.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.CASE_UNKNOWN, number), Icon.BAD).build());
            backButton(canvas);
            return;
        }
        CaseView view = found.get();
        Stage stage = Stage.valueOf(view.stage());
        Optional<Role> role = view.myRole().map(Role::valueOf);
        renderParties(canvas, view);
        renderRecord(canvas, view);
        renderActions(canvas, view, stage, role);
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        canvas.set(49, Icon.of(Items.PAPER).name(GuiKeys.CASE_ACT_CHAT.text(), Icon.TITLE).build(), click -> {
            Feedback.click(click.player());
            click.player().closeContainer();
            Components.send(click.player(), civ.justice().show(Players.caller(click.player()), number));
        });
    }

    private void renderParties(Canvas canvas, CaseView view) {
        canvas.set(4, CourtScreen.caseIcon(view));
        canvas.set(0, Icon.of(Items.BELL).name(GuiKeys.CASE_JUDGE.text(view.judge().orElse("-")), Icon.TITLE).build());
        canvas.set(1, Icon.of(Items.IRON_SWORD).name(GuiKeys.CASE_PROSECUTOR.text(view.prosecutor().orElse("-")),
                Icon.TITLE).build());
        canvas.set(2, Icon.of(Items.SHIELD).name(GuiKeys.CASE_DEFENSE.text(view.defense().orElse("-")), Icon.TITLE)
                .build());
        Icon witnesses = Icon.of(Items.SPYGLASS).name(GuiKeys.CASE_WITNESSES.text(view.witnesses().size()), Icon.TITLE);
        view.witnesses().forEach(name -> witnesses.line(Icon.literal(name, net.minecraft.ChatFormatting.GRAY)));
        canvas.set(6, witnesses.build());
        Icon jury = Icon.of(Items.PLAYER_HEAD).name(GuiKeys.CASE_JURY.text(view.jurors().size()), Icon.TITLE);
        view.jurors().forEach(name -> jury.line(Icon.literal(name, net.minecraft.ChatFormatting.GRAY)));
        canvas.set(7, jury.build());
        if (!view.target().isEmpty()) {
            canvas.set(5, Icon.of(Items.NAME_TAG).name(Message.of(MessageKey.CASE_TARGET, view.target()), Icon.INFO)
                    .build());
        }
        view.appealOf().ifPresent(original -> canvas.set(3, Icon.of(Items.LADDER)
                .name(GuiKeys.CASE_APPEAL_OF.text(original), Icon.INFO).hint(GuiKeys.CASE_OPEN_HINT.text()).build(),
                click -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new CaseScreen(civ, click.player(), original, Optional.of(this)));
                }));
        view.verdict().ifPresent(verdict -> {
            Icon icon = Icon.of(view.upheld() ? Items.IRON_BARS : Items.FEATHER)
                    .name(GuiKeys.CASE_VERDICT_ICON.text(), view.upheld() ? Icon.BAD : Icon.GOOD);
            wrap(Components.of(verdict).getString()).forEach(line -> icon.line(Icon.literal(line,
                    net.minecraft.ChatFormatting.GRAY)));
            canvas.set(8, icon.glint().build());
        });
    }

    private void renderRecord(Canvas canvas, CaseView view) {
        for (int index = 0; index < Math.min(EVIDENCE_SLOTS, view.evidence().size()); index++) {
            CaseView.EvidenceLine evidence = view.evidence().get(index);
            Icon icon = Icon.of((net.minecraft.world.level.ItemLike) (evidence.kind().equals("vertrag") ? Items.FILLED_MAP
                            : evidence.kind().equals("gegenstand") ? Items.CHEST : Items.BOOK))
                    .name(GuiKeys.CASE_EVIDENCE_ITEM.text(evidence.number(), evidence.title()), Icon.TITLE)
                    .line(GuiKeys.CASE_EVIDENCE_BY.text(evidence.by(), evidence.side(), evidence.weight()), Icon.HINT);
            wrap(evidence.content()).stream().limit(8).forEach(line -> icon.line(Icon.literal(line,
                    net.minecraft.ChatFormatting.GRAY)));
            canvas.set(FIRST_EVIDENCE + index, icon.build());
        }
        List<CaseView.StatementLine> statements = view.statements();
        int skip = Math.max(0, statements.size() - STATEMENT_SLOTS);
        for (int index = skip; index < statements.size(); index++) {
            CaseView.StatementLine statement = statements.get(index);
            Icon icon = Icon.of((net.minecraft.world.level.ItemLike) (statement.tendency() > 0 ? Items.DYE.red()
                            : statement.tendency() < 0 ? Items.DYE.lime() : Items.DYE.gray()))
                    .name(GuiKeys.CASE_STATEMENT_ITEM.text(statement.speaker(),
                            JusticeService.roleName(Role.valueOf(statement.role()))), Icon.TITLE);
            wrap("„" + statement.text() + "“").forEach(line -> icon.line(Icon.literal(line,
                    net.minecraft.ChatFormatting.GRAY)));
            canvas.set(FIRST_STATEMENT + index - skip, icon.build());
        }
    }

    private void renderActions(Canvas canvas, CaseView view, Stage stage, Optional<Role> role) {
        String base = "/civ gericht ";
        boolean judge = role.filter(Role.JUDGE::equals).isPresent() || (role.isEmpty() && stage == Stage.FILED);
        boolean side = role.filter(r -> r == Role.PLAINTIFF || r == Role.DEFENDANT || r == Role.PROSECUTOR
                || r == Role.DEFENSE).isPresent();
        boolean early = stage == Stage.FILED || stage == Stage.EVIDENCE;
        if (judge && stage == Stage.FILED) {
            canvas.set(36, Icon.of(Items.DYE.lime()).name(GuiKeys.CASE_ACT_OPEN.text(), Icon.GOOD).build(),
                    click -> act(click, () -> civ.justice().open(Players.caller(click.player()), number)));
        }
        if (judge && (early || stage == Stage.PLEADINGS)) {
            canvas.set(37, Icon.of(Items.BARRIER).name(GuiKeys.CASE_ACT_DISMISS.text(), Icon.BAD).build(),
                    click -> prompt(click, GuiKeys.CASE_ACT_DISMISS.text(), base + "einstellen " + number + " "));
        }
        if (judge && (stage == Stage.EVIDENCE || stage == Stage.PLEADINGS)) {
            canvas.set(38, Icon.of(Items.CLOCK).name(GuiKeys.CASE_ACT_ADVANCE.text(), Icon.TITLE).build(),
                    click -> act(click, () -> civ.justice().advance(Players.caller(click.player()), number)));
        }
        if (judge && (stage == Stage.PLEADINGS || stage == Stage.DELIBERATION)) {
            canvas.set(39, Icon.of(Items.ANVIL).name(GuiKeys.CASE_ACT_VERDICT.text(), Icon.TITLE)
                            .line(Message.of(MessageKey.CASE_VERDICT_FORMAT), Icon.HINT).build(),
                    click -> prompt(click, GuiKeys.CASE_ACT_VERDICT.text(), base + "urteil " + number + " "));
        }
        if (side && early) {
            canvas.set(40, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.CASE_ACT_EVIDENCE.text(), Icon.TITLE)
                            .line(GuiKeys.CASE_ACT_EVIDENCE_LINE.text(), Icon.HINT).build(),
                    click -> act(click, () -> de.etiennebader.civ.fabric.CivCommandTree.handEvidence(civ, click.player(), number)));
            canvas.set(41, Icon.of(Items.SPYGLASS).name(GuiKeys.CASE_ACT_SUMMON.text(), Icon.TITLE).build(),
                    click -> prompt(click, GuiKeys.CASE_ACT_SUMMON.text(), base + "zeuge " + number + " "));
        }
        if ((side || role.filter(Role.WITNESS::equals).isPresent()) && early) {
            canvas.set(42, Icon.of(Items.FEATHER).name(GuiKeys.CASE_ACT_TESTIFY.text(), Icon.TITLE).build(),
                    click -> prompt(click, GuiKeys.CASE_ACT_TESTIFY.text(), base + "aussage " + number + " "));
        }
        if (side && stage == Stage.PLEADINGS) {
            canvas.set(42, Icon.of(Items.GOAT_HORN).name(GuiKeys.CASE_ACT_PLEAD.text(), Icon.TITLE).build(),
                    click -> prompt(click, GuiKeys.CASE_ACT_PLEAD.text(), base + "plaedoyer " + number + " "));
        }
        if (role.filter(Role.DEFENDANT::equals).isPresent() && early && view.defense().isEmpty()) {
            canvas.set(43, Icon.of(Items.SHIELD).name(GuiKeys.CASE_ACT_DEFENSE.text(), Icon.TITLE).build(),
                    click -> prompt(click, GuiKeys.CASE_ACT_DEFENSE.text(), base + "verteidiger " + number + " "));
        }
        if (role.filter(Role.JUROR::equals).isPresent() && (stage == Stage.PLEADINGS || stage == Stage.DELIBERATION)) {
            canvas.set(40, Icon.of(Items.WOOL.red()).name(GuiKeys.CASE_ACT_GUILTY.text(), Icon.BAD).build(),
                    click -> act(click, () -> civ.justice().vote(Players.caller(click.player()), number, true)));
            canvas.set(41, Icon.of(Items.WOOL.lime()).name(GuiKeys.CASE_ACT_INNOCENT.text(), Icon.GOOD).build(),
                    click -> act(click, () -> civ.justice().vote(Players.caller(click.player()), number, false)));
        }
        if (stage == Stage.DECIDED && side) {
            canvas.set(44, Icon.of(Items.LADDER).name(GuiKeys.CASE_ACT_APPEAL.text(), Icon.TITLE).build(),
                    click -> act(click, () -> civ.justice().appeal(Players.caller(click.player()), number)));
        }
    }

    private void act(Click click, Supplier<Reply> action) {
        Feedback.click(click.player());
        report(click, action.get());
        Screens.open(click.player(), this);
    }

    /** Bricht Text in Zeilen für Tooltips. */
    static List<String> wrap(String text) {
        List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() + word.length() + 1 > TEXT_WIDTH && !line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }
}
