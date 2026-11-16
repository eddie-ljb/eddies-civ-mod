package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.office.OfficeView;
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
 * Die Wahlurne: je laufender Wahl der Gemeinde am Ort und ihrer Bünde eine Zeile mit den Kandidaten. Ein Klick wählt;
 * Stimmen an der Urne zählen auch dort, wo das Recht die Urne vorschreibt.
 */
public final class BallotBoxScreen extends MenuScreen {

    private static final int ROWS_FOR_ELECTIONS = 5;
    private static final int CANDIDATES_PER_ROW = 8;

    private final Optional<BlockPosition> position;

    public BallotBoxScreen(CivServer civ, ServerPlayer player, BlockPosition position) {
        this(civ, player, Optional.of(position));
    }

    private BallotBoxScreen(CivServer civ, ServerPlayer player, Optional<BlockPosition> position) {
        super(civ, player, Optional.empty());
        this.position = position;
    }

    /** Briefwahl am Briefkasten: Wahlen der eigenen Gemeinde und Bünde, in denen Briefwahl erlaubt ist. */
    public static BallotBoxScreen postal(CivServer civ, ServerPlayer player) {
        return new BallotBoxScreen(civ, player, Optional.empty());
    }

    private List<PolityView> polities() {
        return position.map(civ.campaigns()::electingPolitiesAt)
                .orElseGet(() -> civ.campaigns().postalPolitiesOf(Players.ref(player)));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(position.isPresent() ? GuiKeys.BALLOT_BOX_TITLE.text() : GuiKeys.POSTAL_VOTE_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.white()).hidden().build());
        backButton(canvas);
        canvas.set(49, Icon.of(Items.BOOK).name(GuiKeys.BALLOT_BOX_INFO.text(), Icon.TITLE)
                .line(GuiKeys.BALLOT_BOX_INFO_LINE.text(), Icon.INFO).build());
        int row = 0;
        for (PolityView polity : polities()) {
            for (OfficeView office : civ.offices().officesOf(polity, Players.ref(player))) {
                if (office.election().isEmpty() || row >= ROWS_FOR_ELECTIONS) {
                    continue;
                }
                renderElection(canvas, row++, polity, office, office.election().get());
            }
        }
        if (row == 0) {
            canvas.set(22, Icon.of(Items.PAPER).name(GuiKeys.BALLOT_BOX_NONE.text(), Icon.INFO).build());
        }
    }

    private void renderElection(Canvas canvas, int row, PolityView polity, OfficeView office, ElectionView election) {
        Icon header = Icon.of(Items.WRITABLE_BOOK)
                .name(GuiKeys.BALLOT_BOX_ELECTION.text(office.name(), polity.name().value()), Icon.TITLE)
                .line(GuiKeys.BALLOT_BOX_CLOSES.text(election.closesOn()), Icon.INFO)
                .line(GuiKeys.OFFICE_BALLOTS.text(election.ballots(), election.electorate()), Icon.INFO);
        if (position.isEmpty()) {
            header.line(GuiKeys.POSTAL_VOTE_LINE.text(), Icon.HINT);
        } else if (polity.laws().valueOf(StandardParameters.BALLOT_BOX_REQUIRED)) {
            header.line(GuiKeys.BALLOT_BOX_REQUIRED_LINE.text(), Icon.HINT);
        }
        canvas.set(row * 9, header.build());
        for (int index = 0; index < Math.min(election.candidates().size(), CANDIDATES_PER_ROW); index++) {
            ElectionView.Candidate candidate = election.candidates().get(index);
            boolean chosen = election.ownChoice().filter(candidate.actor()::equals).isPresent();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(candidate.name()));
            Icon icon = Icon.of(head)
                    .name(Icon.literal(candidate.name(), chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .line(GuiKeys.OFFICE_PLATFORM.text(Message.of(TranslationKeys.platform(candidate.platform()))));
            candidate.party().ifPresent(party -> icon.line(GuiKeys.OFFICE_PARTY.text(party), Icon.HINT));
            for (CampaignService.PromiseView promise : civ.campaigns().promisesOf(polity, candidate.actor())) {
                icon.line(GuiKeys.CANDIDATE_PROMISE.text(promise.text(),
                        CampaignService.statusMessage(promise.kept())), Icon.INFO);
            }
            if (chosen) {
                icon.line(GuiKeys.OFFICE_OWN_CHOICE.text(), Icon.GOOD).glint();
            }
            canvas.set(row * 9 + 1 + index, icon.blank().hint(GuiKeys.OFFICE_VOTE_HINT.text()).build(), click ->
                    report(click, civ.offices().vote(caller(), polity.id(), office.office(), candidate.actor(), true)));
        }
    }
}
