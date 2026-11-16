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
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.parliament.ParliamentService;
import de.etiennebader.civ.server.parliament.ParliamentView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Das Parlament einer Gemeinde oder eines Bundes.
 *
 * <p>Sitzverteilung: oben ein Sitz je Abgeordnetem im Wappen seiner Fraktion, das Präsidium leuchtet; darunter die
 * Fraktionen. Während einer Parlamentswahl lässt sich auf die Kandidatenliste umschalten: Klick auf einen Kandidaten
 * wählt ihn. Unten: kandidieren bzw. Wahl ausrufen, Tagesordnung (Abstimmungen) und die Regeln.
 */
public final class ParliamentScreen extends MenuScreen {

    private static final int FIRST_SEAT = 9;
    private static final int SEAT_SLOTS = 27;
    private static final int FIRST_FACTION = 36;
    private static final int FACTION_SLOTS = 9;
    private static final int FIRST_CANDIDATE = 9;
    private static final int CANDIDATE_SLOTS = 36;

    private final PolityId polity;
    private final String initialName;
    private boolean showCandidates;

    public ParliamentScreen(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
        super(civ, player, parent);
        this.polity = polity.id();
        this.initialName = polity.name().value();
        this.showCandidates = !polity.parliament().seated() && polity.electionFor(Office.PARLIAMENT).isPresent();
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.PARLIAMENT_TITLE.text(initialName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.blue()).hidden().build());
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.blue()).hidden().build());
        backButton(canvas);
        Optional<ParliamentView> found = civ.parliaments().view(polity, Players.ref(player));
        if (found.isEmpty()) {
            return;
        }
        ParliamentView view = found.get();
        canvas.set(4, info(view));
        boolean candidates = showCandidates && view.election().isPresent();
        if (candidates) {
            renderCandidates(canvas, view.election().get());
        } else {
            renderSeats(canvas, view);
        }
        view.election().ifPresent(election -> canvas.set(46, Icon.of(candidates ? Items.BANNER.white() : Items.PAPER)
                .name(candidates ? GuiKeys.PARLIAMENT_SHOW_SEATS.text() : GuiKeys.PARLIAMENT_SHOW_CANDIDATES.text(),
                        Icon.TITLE)
                .line(GuiKeys.PARLIAMENT_ELECTION_UNTIL.text(election.closesOn()), Icon.INFO).build(), click -> {
            Feedback.click(click.player());
            showCandidates = !showCandidates;
            Screens.open(click.player(), this);
        }));
        canvas.set(48, electionButton(view), click -> {
            if (view.election().isPresent() && !view.election().get().ownCandidacy()) {
                Feedback.click(click.player());
                Screens.open(click.player(), new PlatformScreen(civ, player, polity, Office.PARLIAMENT, this));
            } else if (view.election().isEmpty() && click.shift()) {
                report(click, civ.offices().callElection(caller(), polity, Office.PARLIAMENT));
                showCandidates = true;
            } else {
                Feedback.click(click.player());
            }
        });
        int open = view.agenda().size();
        Icon agenda = Icon.of(Items.LECTERN).name(GuiKeys.PARLIAMENT_AGENDA.text(open), Icon.TITLE)
                .line(view.member() ? GuiKeys.PARLIAMENT_YOU_VOTE.text() : GuiKeys.PARLIAMENT_MEMBERS_VOTE.text(),
                        view.member() ? Icon.GOOD : Icon.INFO)
                .hint(GuiKeys.PARLIAMENT_AGENDA_HINT.text()).count(Math.max(1, open));
        if (open > 0) {
            agenda.glint();
        }
        canvas.set(50, agenda.build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new VotesScreen(civ, player, polity, view.name(), this));
        });
        civ.queries().polity(polity).ifPresent(current -> {
            canvas.set(52, rules(current));
            canvas.set(53, Icon.of(Items.SPYGLASS).name(GuiKeys.ELECTIONS_OPEN.text(), Icon.TITLE)
                    .hint(GuiKeys.ELECTIONS_OPEN_HINT.text()).build(), click -> {
                Feedback.click(click.player());
                Screens.open(click.player(), new ElectionsScreen(civ, player, current, Optional.of(this)));
            });
        });
    }

    private ItemStack info(ParliamentView view) {
        Icon icon = Icon.of(Items.BELL).name(GuiKeys.PARLIAMENT_INFO.text(view.name()), Icon.TITLE);
        if (view.seatsByLaw() <= 0 && !view.seated()) {
            return icon.line(GuiKeys.PARLIAMENT_NONE.text(), Icon.BAD)
                    .hint(GuiKeys.PARLIAMENT_NONE_HINT.text()).build();
        }
        if (!view.seated()) {
            icon.line(GuiKeys.PARLIAMENT_NOT_ELECTED.text(view.seatsByLaw()), Icon.HINT);
        } else {
            icon.line(GuiKeys.PARLIAMENT_SEATS.text(view.seats(), view.seatsByLaw()), Icon.INFO)
                    .line(GuiKeys.PARLIAMENT_ELECTED_ON.text(view.electedOn().orElse(0L)), Icon.INFO);
            view.until().ifPresent(until -> icon.line(GuiKeys.PARLIAMENT_UNTIL.text(until), Icon.INFO));
            view.president().ifPresent(president -> icon.line(GuiKeys.PARLIAMENT_PRESIDENT.text(president),
                    Icon.GOOD));
        }
        if (view.member()) {
            icon.blank().line(GuiKeys.PARLIAMENT_OWN_SEAT.text(), Icon.GOOD);
        }
        return icon.build();
    }

    private void renderSeats(Canvas canvas, ParliamentView view) {
        if (!view.seated()) {
            canvas.set(22, Icon.of(Items.STRUCTURE_VOID).name(GuiKeys.PARLIAMENT_EMPTY.text(), Icon.INFO)
                    .hint(GuiKeys.PARLIAMENT_EMPTY_HINT.text()).build());
            return;
        }
        List<ItemStack> seats = new ArrayList<>();
        for (ParliamentView.Faction faction : view.factions()) {
            Object factionName = ParliamentService.factionName(faction);
            for (ParliamentView.Member member : faction.members()) {
                Icon seat = Icon.of(PartiesScreen.emblem(faction.emblem()))
                        .name(Icon.literal(member.name(), member.president() ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                        .line(GuiKeys.PARLIAMENT_FACTION.text(factionName), Icon.INFO)
                        .line(GuiKeys.PARLIAMENT_VOTES.text(votes(member.votes())), Icon.INFO)
                        .line(member.villager() ? GuiKeys.PARLIAMENT_VILLAGER.text() : GuiKeys.PARLIAMENT_PLAYER.text(),
                                Icon.HINT);
                if (member.president()) {
                    seat.line(GuiKeys.PARLIAMENT_PRESIDENT_LINE.text(), Icon.GOOD).glint();
                }
                seats.add(seat.build());
            }
        }
        for (int index = 0; index < Math.min(seats.size(), SEAT_SLOTS); index++) {
            canvas.set(FIRST_SEAT + index, seats.get(index));
        }
        if (seats.size() > SEAT_SLOTS) {
            canvas.set(FIRST_SEAT + SEAT_SLOTS - 1, Icon.of(Items.PAPER)
                    .name(GuiKeys.PARLIAMENT_MORE_SEATS.text(seats.size() - SEAT_SLOTS + 1), Icon.INFO).build());
        }
        for (int index = 0; index < Math.min(view.factions().size(), FACTION_SLOTS); index++) {
            ParliamentView.Faction faction = view.factions().get(index);
            int size = faction.members().size();
            canvas.set(FIRST_FACTION + index, Icon.of(PartiesScreen.emblem(faction.emblem()))
                    .name(GuiKeys.PARLIAMENT_FACTION_NAME.text(ParliamentService.factionName(faction)), Icon.TITLE)
                    .line(GuiKeys.PARLIAMENT_FACTION_SEATS.text(size, percent(size, view.seats())), Icon.INFO)
                    .count(Math.max(1, Math.min(64, size))).build());
        }
    }

    private void renderCandidates(Canvas canvas, ElectionView election) {
        if (election.candidates().isEmpty()) {
            canvas.set(22, Icon.of(Items.PAPER).name(GuiKeys.OFFICE_NO_CANDIDATES.text(), Icon.INFO)
                    .hint(GuiKeys.OFFICE_STAND_HINT.text()).build());
            return;
        }
        List<ElectionView.Candidate> candidates = election.candidates();
        for (int index = 0; index < Math.min(candidates.size(), CANDIDATE_SLOTS); index++) {
            ElectionView.Candidate candidate = candidates.get(index);
            boolean chosen = election.ownChoice().filter(candidate.actor()::equals).isPresent();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(candidate.name()));
            Icon icon = Icon.of(head)
                    .name(Icon.literal(candidate.name(), chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .line(GuiKeys.OFFICE_PLATFORM.text(Message.of(TranslationKeys.platform(candidate.platform()))));
            candidate.party().ifPresentOrElse(party -> icon.line(GuiKeys.OFFICE_PARTY.text(party), Icon.HINT),
                    () -> icon.line(GuiKeys.PARLIAMENT_NO_PARTY.text(), Icon.HINT));
            civ.queries().polity(polity).ifPresent(current -> civ.campaigns().promisesOf(current, candidate.actor())
                    .forEach(promise -> icon.line(GuiKeys.CANDIDATE_PROMISE.text(promise.text(),
                            CampaignService.statusMessage(promise.kept())), Icon.INFO)));
            if (chosen) {
                icon.line(GuiKeys.OFFICE_OWN_CHOICE.text(), Icon.GOOD).glint();
            }
            canvas.set(FIRST_CANDIDATE + index, icon.blank().hint(GuiKeys.OFFICE_VOTE_HINT.text()).build(), click ->
                    report(click, civ.offices().vote(caller(), polity, Office.PARLIAMENT, candidate.actor())));
        }
    }

    private static ItemStack electionButton(ParliamentView view) {
        if (view.election().isPresent()) {
            ElectionView election = view.election().get();
            Icon icon = Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.PARLIAMENT_ELECTION.text(election.closesOn()), Icon.GOOD)
                    .line(GuiKeys.OFFICE_BALLOTS.text(election.ballots(), election.electorate()), Icon.INFO)
                    .line(GuiKeys.PARLIAMENT_CANDIDATES.text(election.candidates().size()), Icon.INFO);
            if (election.ownCandidacy()) {
                icon.line(GuiKeys.OFFICE_OWN_CANDIDACY.text(), Icon.GOOD);
            } else {
                icon.hint(GuiKeys.OFFICE_STAND_HINT.text());
            }
            return icon.glint().build();
        }
        return Icon.of(Items.BOOK).name(GuiKeys.PARLIAMENT_NO_ELECTION.text(), Icon.TITLE)
                .hint(GuiKeys.PARLIAMENT_CALL_HINT.text()).build();
    }

    private static ItemStack rules(PolityView polity) {
        long seats = polity.laws().valueOf(StandardParameters.PARLIAMENT_SEATS);
        long term = polity.laws().valueOf(StandardParameters.TERM_DAYS);
        return Icon.of(Items.BOOK).name(GuiKeys.OFFICE_RULES.text(), Icon.TITLE)
                .line(GuiKeys.PARLIAMENT_RULES_SEATS.text(seats))
                .line(term > 0 ? GuiKeys.OFFICE_RULES_TERM.text(term) : GuiKeys.OFFICE_RULES_TERM_OPEN.text())
                .line(GuiKeys.OFFICE_RULES_DURATION.text(polity.laws().valueOf(StandardParameters.ELECTION_DAYS)))
                .blank()
                .line(GuiKeys.PARLIAMENT_RULES_METHOD.text(), Icon.HINT)
                .line(GuiKeys.PARLIAMENT_RULES_LEGISLATES.text(), Icon.HINT)
                .build();
    }

    /** Gewichtete Stimmen als Text; 100 sind eine Bürgerstimme. */
    private static String votes(long weighted) {
        long whole = weighted / 100;
        long rest = weighted % 100;
        return rest == 0 ? Long.toString(whole) : whole + "," + String.format(java.util.Locale.ROOT, "%02d", rest);
    }

    private static long percent(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100.0 / total);
    }
}
