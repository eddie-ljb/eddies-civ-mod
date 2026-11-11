package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
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
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.office.OfficeView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;

/**
 * Ämter und Wahlen. Drei Spalten, je Amt: oben das Amt mit Inhaber und laufender Wahl, darunter die Kandidaten.
 * Klick aufs Amt: bei laufender Wahl kandidieren, sonst mit Umschalt eine Wahl ausrufen. Klick auf einen Kandidaten:
 * ihn wählen.
 */
final class OfficesScreen extends MenuScreen {

    /** Linke Spalte je Amt; das Amt steht in der Mitte seiner drei Spalten. */
    private static final int[] FIRST_COLUMN = {0, 3, 6};
    private static final int CANDIDATES_PER_OFFICE = 6;

    private final PolityId town;
    private final String townName;

    OfficesScreen(CivServer civ, ServerPlayer player, PolityId town, String townName, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
        this.townName = townName;
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.OFFICES_TITLE.text(townName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        Optional<PolityView> polity = civ.queries().polity(town);
        if (polity.isEmpty()) {
            return;
        }
        canvas.set(40, rules(polity.get()));
        List<OfficeView> offices = civ.offices().officesOf(polity.get(), Players.ref(player));
        for (int index = 0; index < offices.size(); index++) {
            renderOffice(canvas, offices.get(index), FIRST_COLUMN[index]);
        }
    }

    private void renderOffice(Canvas canvas, OfficeView office, int column) {
        canvas.set(9 + column + 1, officeIcon(office), click -> {
            if (office.election().isPresent() && !office.election().get().ownCandidacy()) {
                Feedback.click(click.player());
                Screens.open(click.player(), new PlatformScreen(civ, player, office.office(), this));
            } else if (office.election().isPresent()) {
                Feedback.click(click.player());
            } else if (click.shift()) {
                report(click, civ.offices().callElection(caller(), office.office().name()));
            } else {
                Feedback.click(click.player());
            }
        });
        if (office.election().isEmpty()) {
            return;
        }
        ElectionView election = office.election().get();
        List<ElectionView.Candidate> candidates = election.candidates();
        if (candidates.isEmpty()) {
            canvas.set(18 + column + 1, Icon.of(Items.PAPER).name(GuiKeys.OFFICE_NO_CANDIDATES.text(), Icon.INFO)
                    .hint(GuiKeys.OFFICE_STAND_HINT.text()).build());
            return;
        }
        for (int index = 0; index < Math.min(candidates.size(), CANDIDATES_PER_OFFICE); index++) {
            ElectionView.Candidate candidate = candidates.get(index);
            int slot = 18 + (index / 3) * 9 + column + index % 3;
            boolean chosen = election.ownChoice().filter(candidate.actor()::equals).isPresent();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(candidate.name()));
            Icon icon = Icon.of(head).name(Icon.literal(candidate.name(), chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .line(GuiKeys.OFFICE_CANDIDATE_FOR.text(office.name()), Icon.INFO)
                    .line(GuiKeys.OFFICE_PLATFORM.text(Message.of(TranslationKeys.platform(candidate.platform()))));
            if (chosen) {
                icon.line(GuiKeys.OFFICE_OWN_CHOICE.text(), Icon.GOOD).glint();
            }
            canvas.set(slot, icon.blank().hint(GuiKeys.OFFICE_VOTE_HINT.text()).build(), click ->
                    report(click, civ.offices().vote(caller(), office.office(), candidate.actor())));
        }
    }

    private static ItemStack officeIcon(OfficeView office) {
        Icon icon = Icon.of(item(office.office())).name(office.name(), Icon.TITLE);
        if (office.holder().isEmpty()) {
            icon.line(GuiKeys.OFFICE_VACANT_LINE.text(), Icon.BAD);
        } else {
            icon.line(GuiKeys.OFFICE_HOLDER.text(office.holder().get()));
            if (office.implicit()) {
                icon.line(GuiKeys.OFFICE_ACTING.text(), Icon.HINT);
            }
            office.until().ifPresent(day -> icon.line(GuiKeys.OFFICE_UNTIL.text(day), Icon.INFO));
        }
        icon.blank();
        if (office.election().isPresent()) {
            ElectionView election = office.election().get();
            icon.line(GuiKeys.OFFICE_ELECTION.text(election.closesOn()), Icon.GOOD)
                    .line(GuiKeys.OFFICE_BALLOTS.text(election.ballots(), election.electorate()), Icon.INFO);
            if (election.ownCandidacy()) {
                icon.line(GuiKeys.OFFICE_OWN_CANDIDACY.text(), Icon.GOOD);
            } else {
                icon.hint(GuiKeys.OFFICE_STAND_HINT.text());
            }
            icon.glint();
        } else {
            icon.hint(GuiKeys.OFFICE_CALL_HINT.text());
        }
        return icon.build();
    }

    private static Item item(Office office) {
        return switch (office) {
            case MAYOR -> Items.GOLDEN_HELMET;
            case TREASURER -> Items.GOLD_INGOT;
            case PLANNER -> Items.BRICKS;
        };
    }

    private static ItemStack rules(PolityView polity) {
        long term = polity.laws().valueOf(StandardParameters.TERM_DAYS);
        long duration = polity.laws().valueOf(StandardParameters.ELECTION_DAYS);
        long villagers = polity.laws().valueOf(StandardParameters.VILLAGER_VOTE_PERCENT);
        return Icon.of(Items.BOOK).name(GuiKeys.OFFICE_RULES.text(), Icon.TITLE)
                .line(term > 0 ? GuiKeys.OFFICE_RULES_TERM.text(term) : GuiKeys.OFFICE_RULES_TERM_OPEN.text())
                .line(GuiKeys.OFFICE_RULES_DURATION.text(duration))
                .line(villagers > 0 ? GuiKeys.OFFICE_RULES_VILLAGERS.text(villagers)
                        : GuiKeys.OFFICE_RULES_NO_VILLAGERS.text())
                .blank().hint(GuiKeys.OFFICE_RULES_HINT.text()).build();
    }
}
