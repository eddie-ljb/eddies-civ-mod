package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
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
import de.etiennebader.civ.server.federation.Level;
import de.etiennebader.civ.server.town.TownOverview;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Das Menü einer Ebene über der Gemeinde, z. B. Land oder Nation: Steckbrief, Ämter, Parlament, Wahlen, Abstimmungen,
 * Recht, Verfassung und Gesetzbuch genau dieser Ebene. Oben wechselt man zwischen den Ebenen der eigenen Kette;
 * die Gemeinde öffnet ihr Gemeindehaus-Menü.
 */
public final class LevelScreen extends MenuScreen {

    /** Flagge (Klick: Banner holen) und Hymne (Rechtsklick: abspielen). */
    private void renderSymbols(Canvas canvas, PolityView current, Level level) {
        Optional<de.etiennebader.civ.domain.civic.Flag> flag = civ.civic().flagOf(current.id());
        Icon flagIcon = flag.map(found -> Icon.of(de.etiennebader.civ.fabric.content.Flags.banner(found,
                        player.level().registryAccess())).name(GuiKeys.LEVEL_FLAG.text(current.name().value()), Icon.TITLE)
                        .hint(GuiKeys.LEVEL_FLAG_HINT.text()))
                .orElseGet(() -> Icon.of(Items.BANNER.white()).name(GuiKeys.LEVEL_FLAG.text(current.name().value()),
                        Icon.TITLE).line(GuiKeys.LEVEL_FLAG_NONE.text(level.id()), Icon.HINT));
        Optional<de.etiennebader.civ.domain.civic.Anthem> anthem = civ.civic().anthemOf(current.id());
        anthem.ifPresentOrElse(found -> flagIcon.line(GuiKeys.LEVEL_ANTHEM.text(found.title()), Icon.INFO)
                        .hint(GuiKeys.LEVEL_ANTHEM_HINT.text()),
                () -> flagIcon.line(GuiKeys.LEVEL_ANTHEM_NONE.text(level.id()), Icon.HINT));
        canvas.set(29, flagIcon.build(), click -> {
            Feedback.click(click.player());
            if (click.right()) {
                anthem.ifPresent(found -> de.etiennebader.civ.fabric.Ambience.play(click.player(), found));
            } else {
                flag.ifPresent(found -> click.player().getInventory().placeItemBackInInventory(
                        de.etiennebader.civ.fabric.content.Flags.banner(found, click.player().level().registryAccess()),
                        net.minecraft.util.Prediction.SERVER_ONLY));
            }
        });
    }

    private static final int MAX_TABS = 9;
    private static final int MAX_CONSTITUENTS = 12;

    private final PolityId polity;
    private final String initialName;

    public LevelScreen(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
        super(civ, player, parent);
        this.polity = polity.id();
        this.initialName = polity.name().value();
    }

    /** Öffnet das passende Menü: Gemeindehaus für Gemeinden, sonst das Ebenen-Menü. */
    public static void open(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
        Feedback.click(player);
        if (polity.kind() == PolityKind.FEDERATION) {
            Screens.open(player, new LevelScreen(civ, player, polity, parent));
        } else {
            Screens.open(player, new TownHallScreen(civ, player, civ.towns().overview(polity, Players.ref(player))));
        }
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        Object level = civ.queries().polity(polity).map(found -> civ.levels().levelOf(found).displayName()).orElse("");
        return Components.of(GuiKeys.LEVEL_TITLE.text(level, initialName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.cyan()).hidden().build());
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.cyan()).hidden().build());
        backButton(canvas);
        Optional<PolityView> found = civ.queries().polity(polity);
        if (found.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.POLITY_GONE), Icon.BAD).build());
            return;
        }
        PolityView current = found.get();
        Level level = civ.levels().levelOf(current);
        renderTabs(canvas, current);
        canvas.set(13, overview(current, level));

        int elections = current.elections().size();
        Icon offices = Icon.of(Items.GOLDEN_HELMET).name(GuiKeys.OFFICES.text(elections))
                .hint(GuiKeys.LEVEL_OFFICES_HINT.text());
        if (elections > 0) {
            offices.glint();
        }
        canvas.set(19, offices.build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new OfficesScreen(civ, player, polity, current.name().value(), this));
        });
        canvas.set(20, Icon.of(Items.LECTERN).name(GuiKeys.PARLIAMENT_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.PARLIAMENT_OPEN_HINT.text()).build(), click -> withPolity(target -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ParliamentScreen(civ, player, target, Optional.of(this)));
                }));
        canvas.set(21, Icon.of(Items.SPYGLASS).name(GuiKeys.ELECTIONS_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.ELECTIONS_OPEN_HINT.text()).build(), click -> withPolity(target -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ElectionsScreen(civ, player, target, Optional.of(this)));
                }));
        int votes = current.proposals().size();
        Icon voteIcon = Icon.of(Items.BELL).name(GuiKeys.VOTES.text(votes)).hint(GuiKeys.VOTES_HINT.text());
        if (votes > 0) {
            voteIcon.glint();
        }
        canvas.set(22, voteIcon.build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new VotesScreen(civ, player, polity, current.name().value(), this));
        });
        canvas.set(23, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.LAWS.text())
                .hint(GuiKeys.LEVEL_LAWS_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new LawsScreen(civ, player, polity, this));
        });
        canvas.set(24, Icon.of(Items.WRITTEN_BOOK).name(GuiKeys.CONSTITUTION_TOWN_HALL.text(), Icon.TITLE)
                .hint(GuiKeys.CONSTITUTION_TOWN_HALL_HINT.text()).build(), click -> withPolity(target -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ConstitutionScreen(civ, player, target, Optional.of(this)));
                }));
        canvas.set(25, Icon.of(Items.ENCHANTED_BOOK).name(GuiKeys.LAW_BOOK_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.LAW_BOOK_OPEN_HINT.text()).build(), click -> withPolity(target -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new LawBookScreen(civ, player, target, Optional.of(this)));
                }));

        canvas.set(31, Icon.of(Items.BELL).name(GuiKeys.COURT_OPEN.text(), Icon.TITLE)
                .line(GuiKeys.COURT_OPEN_LINE.text(), Icon.INFO)
                .hint(GuiKeys.CASE_OPEN_HINT.text()).build(), click -> withPolity(target -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new CourtScreen(civ, player, Optional.of(target), Optional.of(this)));
                }));
        renderSymbols(canvas, current, level);

        canvas.set(40, Icon.of(Items.KNOWLEDGE_BOOK).name(GuiKeys.LEVEL_COMMANDS.text(level.displayName()), Icon.TITLE)
                .line(GuiKeys.LEVEL_COMMAND_LINE.text("/civ " + level.id() + " aemter|parlament|umfrage"))
                .line(GuiKeys.LEVEL_COMMAND_LINE.text("/civ " + level.id() + " wahl ausrufen parlament"))
                .line(GuiKeys.LEVEL_COMMAND_LINE.text("/civ " + level.id() + " wahl kandidieren <Amt>"))
                .line(GuiKeys.LEVEL_COMMAND_LINE.text("/civ " + level.id() + " recht setzen <Regel> <Wert>"))
                .line(GuiKeys.LEVEL_COMMAND_LINE.text("/civ " + level.id() + " abstimmungen|verfassung|gesetze"))
                .hint(GuiKeys.LEVEL_COMMANDS_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    Components.send(click.player(), civ.levels().info(caller(), level.id()));
                });
    }

    /** Oben je Ebene ein Reiter: die eigene Kette, die aktuelle Ebene leuchtet. */
    private void renderTabs(Canvas canvas, PolityView current) {
        List<PolityView> chain = civ.levels().chainOf(Players.ref(player));
        List<Level> levels = civ.levels().levels();
        for (int index = 0; index < Math.min(levels.size(), MAX_TABS); index++) {
            Level level = levels.get(index);
            Optional<PolityView> member = chain.stream().filter(polity -> polity.level().equals(level.id())).findFirst();
            if (member.isEmpty()) {
                canvas.set(index, Icon.of(Items.STAINED_GLASS_PANE.gray())
                        .name(GuiKeys.LEVEL_TAB_NONE.text(level.displayName()), Icon.HINT).build());
                continue;
            }
            PolityView target = member.get();
            boolean here = target.id().equals(current.id());
            Icon tab = Icon.of(target.kind() == PolityKind.FEDERATION ? Items.BEACON : Items.BELL)
                    .name(GuiKeys.LEVEL_TAB.text(level.displayName(), target.name().value()), Icon.TITLE)
                    .line(GuiKeys.OVERVIEW_MAYOR.text(target.mayor().map(civ.texts()::actor).orElse("-")));
            if (here) {
                tab.glint().line(GuiKeys.LEVEL_TAB_CURRENT.text(), Icon.GOOD);
            } else {
                tab.hint(GuiKeys.LEVEL_TAB_HINT.text());
            }
            canvas.set(index, tab.build(), click -> {
                if (!here) {
                    civ.queries().polity(target.id()).ifPresent(fresh -> open(civ, click.player(), fresh,
                            Optional.of(this)));
                }
            });
        }
    }

    private net.minecraft.world.item.ItemStack overview(PolityView current, Level level) {
        TownOverview overview = civ.towns().overview(current, Players.ref(player));
        Icon icon = Icon.of(Items.BEACON).name(GuiKeys.OVERVIEW.text(current.name().value()))
                .line(GuiKeys.LEVEL_KIND.text(level.displayName()), Icon.INFO)
                .line(GuiKeys.OVERVIEW_FOUNDED.text(current.foundedOn().value()))
                .line(GuiKeys.LEVEL_HEAD.text(current.mayor().map(civ.texts()::actor).orElse("-")))
                .line(GuiKeys.OVERVIEW_MEMBERS.text(overview.members().size()))
                .line(GuiKeys.OVERVIEW_TREASURY.text(money(overview.treasury())));
        List<String> constituents = current.constituents().stream()
                .map(ref -> civ.queries().polity(ref.id()).map(polity -> polity.name().value()).orElse("?"))
                .toList();
        icon.line(GuiKeys.LEVEL_CONSTITUENTS.text(constituents.size()), Icon.TITLE);
        for (String name : constituents.subList(0, Math.min(constituents.size(), MAX_CONSTITUENTS))) {
            icon.line(GuiKeys.LEVEL_CONSTITUENT.text(name));
        }
        civ.queries().parentOf(current.id()).ifPresent(parent ->
                icon.line(GuiKeys.LEVEL_PARENT.text(parent.name().value()), Icon.INFO));
        icon.blank().line(GuiKeys.OVERVIEW_ROLE.text(TownHallScreen.role(overview.viewer())),
                overview.viewer().isMember() ? Icon.GOOD : Icon.HINT);
        return icon.glint().build();
    }

    private void withPolity(java.util.function.Consumer<PolityView> action) {
        civ.queries().polity(polity).ifPresent(action);
    }
}
