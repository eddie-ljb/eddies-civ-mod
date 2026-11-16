package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.justice.Stage;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.justice.CaseView;
import de.etiennebader.civ.server.justice.JusticeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Ein Gericht: Klage einreichen, Personal, laufende und entschiedene Verfahren. Ohne Gericht (kein Mitglied einer
 * Gemeinde) zeigt es nur die eigenen Verfahren.
 */
public final class CourtScreen extends MenuScreen {

    private static final int FIRST_CASE = 9;
    private static final int CASE_SLOTS = 36;

    private final Optional<PolityView> court;
    private boolean mine;

    public CourtScreen(CivServer civ, ServerPlayer player, Optional<PolityView> court, Optional<Screen> parent) {
        super(civ, player, parent);
        this.court = court;
        this.mine = court.isEmpty();
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(mine || court.isEmpty() ? GuiKeys.COURT_MINE_TITLE.text()
                : GuiKeys.COURT_TITLE.text(court.get().name().value()));
    }

    @Override
    public void render(Canvas canvas) {
        List<CaseView> cases = court.isPresent() && !mine
                ? civ.justice().casesOf(court.get(), Players.ref(player))
                : civ.justice().casesOf(Players.ref(player));
        court.ifPresent(polity -> renderHeader(canvas, polity));
        if (court.isEmpty()) {
            canvas.set(4, Icon.of(Items.BOOK).name(GuiKeys.COURT_NO_COURT.text(), Icon.INFO).build());
        }
        int own = civ.justice().casesOf(Players.ref(player)).size();
        canvas.set(8, Icon.of(mine ? Items.WRITABLE_BOOK : Items.BOOK).name(GuiKeys.COURT_MY_CASES.text(own), Icon.TITLE)
                .glint().build(), click -> {
                    Feedback.click(click.player());
                    if (court.isPresent()) {
                        mine = !mine;
                        Screens.open(click.player(), this);
                    }
                });
        for (int index = 0; index < Math.min(CASE_SLOTS, cases.size()); index++) {
            CaseView view = cases.get(index);
            canvas.set(FIRST_CASE + index, caseIcon(view), click -> {
                Feedback.click(click.player());
                Screens.open(click.player(), new CaseScreen(civ, click.player(), view.number(), Optional.of(this)));
            });
        }
        if (cases.isEmpty()) {
            canvas.set(22, Icon.of(Items.PAPER).name(Message.of(MessageKey.COURT_NO_CASES), Icon.INFO).build());
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
    }

    private void renderHeader(Canvas canvas, PolityView polity) {
        String command = "/civ ebene " + polity.level() + " gericht klage ";
        canvas.set(0, Icon.of(Items.IRON_SWORD).name(GuiKeys.COURT_FILE_CRIMINAL.text(), Icon.TITLE)
                .line(GuiKeys.COURT_FILE_CRIMINAL_LINE.text(), Icon.INFO)
                .line(Message.of(MessageKey.CASE_FORMAT), Icon.HINT)
                .hint(GuiKeys.COURT_FILE_HINT.text()).build(),
                click -> prompt(click, GuiKeys.COURT_FILE_CRIMINAL.text(), command + "straf "));
        canvas.set(1, Icon.of(Items.EMERALD).name(GuiKeys.COURT_FILE_CIVIL.text(), Icon.TITLE)
                .line(GuiKeys.COURT_FILE_CIVIL_LINE.text(), Icon.INFO)
                .hint(GuiKeys.COURT_FILE_HINT.text()).build(),
                click -> prompt(click, GuiKeys.COURT_FILE_CIVIL.text(), command + "zivil "));
        canvas.set(2, Icon.of(Items.WRITTEN_BOOK).name(GuiKeys.COURT_FILE_CONSTITUTIONAL.text(), Icon.TITLE)
                .line(GuiKeys.COURT_FILE_CONSTITUTIONAL_LINE.text(), Icon.INFO)
                .hint(GuiKeys.COURT_FILE_HINT.text()).build(),
                click -> prompt(click, GuiKeys.COURT_FILE_CONSTITUTIONAL.text(), command + "verfassung "));
        var staff = civ.queries().justice().staffOf(polity.id());
        String judges = staff.judges().isEmpty() ? polity.mayor().map(civ.texts()::actor).orElse("-")
                : String.join(", ", staff.judges().stream().map(civ.texts()::actor).toList());
        String prosecutors = staff.prosecutors().isEmpty() ? "-"
                : String.join(", ", staff.prosecutors().stream().map(civ.texts()::actor).toList());
        canvas.set(4, Icon.of(Items.BELL).name(GuiKeys.COURT_TITLE.text(polity.name().value()), Icon.TITLE)
                .line(GuiKeys.COURT_JUDGES.text(judges), Icon.INFO)
                .line(GuiKeys.COURT_PROSECUTORS.text(prosecutors), Icon.INFO)
                .line(GuiKeys.COURT_FEE.text(MoneyFormat.whole(polity.laws().valueOf(StandardParameters.COURT_FEE))),
                        Icon.HINT)
                .line(GuiKeys.COURT_JURY.text(polity.laws().valueOf(StandardParameters.JURY_SIZE)), Icon.HINT)
                .build());
    }

    static ItemStack caseIcon(CaseView view) {
        Stage stage = Stage.valueOf(view.stage());
        Icon icon = Icon.of(itemFor(stage, view.upheld()))
                .name(GuiKeys.CASE_TITLE.text(view.number()), Icon.TITLE)
                .line(GuiKeys.CASE_VERSUS.text(view.plaintiff(), view.defendant()), Icon.INFO)
                .line(GuiKeys.CASE_CHARGE_LINE.text(view.charge()), Icon.INFO)
                .line(GuiKeys.CASE_STAGE_LINE.text(JusticeService.stageName(stage), view.stageUntil()), Icon.HINT);
        view.myRole().ifPresent(role -> icon.line(GuiKeys.CASE_ROLE_LINE.text(
                JusticeService.roleName(de.etiennebader.civ.domain.justice.Role.valueOf(role))), Icon.GOOD));
        return icon.hint(GuiKeys.CASE_OPEN_HINT.text()).build();
    }

    static Item itemFor(Stage stage, boolean upheld) {
        return switch (stage) {
            case FILED -> Items.PAPER;
            case EVIDENCE -> Items.SPYGLASS;
            case PLEADINGS -> Items.WRITABLE_BOOK;
            case DELIBERATION -> Items.CLOCK;
            case DECIDED -> Items.ANVIL;
            case FINAL -> upheld ? Items.IRON_BARS : Items.FEATHER;
            case DISMISSED -> Items.BARRIER;
            case APPEALED -> Items.LADDER;
        };
    }
}
