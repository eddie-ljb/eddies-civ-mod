package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.vote.ProposalView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Offene Abstimmungen der Gemeinde. Linksklick stimmt dafür, Rechtsklick dagegen.
 */
final class VotesScreen extends MenuScreen {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final PolityId town;
    private final String townName;

    VotesScreen(CivServer civ, ServerPlayer player, PolityId town, String townName, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
        this.townName = townName;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.VOTES_TITLE.text(townName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(3, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        Optional<PolityView> polity = civ.queries().polity(town);
        if (polity.isEmpty()) {
            return;
        }
        List<ProposalView> proposals = civ.voting().proposalsOf(polity.get(), Players.ref(player));
        if (proposals.isEmpty()) {
            canvas.set(13, Icon.of(Items.PAPER).name(GuiKeys.VOTES_EMPTY.text(), Icon.INFO).build());
            return;
        }
        for (int index = 0; index < Math.min(proposals.size(), SLOTS.length); index++) {
            ProposalView proposal = proposals.get(index);
            canvas.set(SLOTS[index], icon(proposal), click ->
                    report(click, civ.voting().vote(caller(), proposal.number(), !click.right())));
        }
    }

    private static ItemStack icon(ProposalView proposal) {
        Icon icon = Icon.of(Items.WRITABLE_BOOK).count(proposal.number())
                .name(GuiKeys.VOTE_ENTRY.text(proposal.law(), proposal.value()))
                .line(GuiKeys.VOTE_TALLY.text(proposal.inFavour(), proposal.against(), proposal.electorate()))
                .line(GuiKeys.VOTE_PROPOSER.text(proposal.proposer()))
                .line(GuiKeys.VOTE_DEADLINE.text(proposal.decidedOn()));
        proposal.ownVote().ifPresent(yes -> icon.line(yes ? GuiKeys.VOTE_OWN_YES.text() : GuiKeys.VOTE_OWN_NO.text(),
                yes ? Icon.GOOD : Icon.BAD));
        return icon.blank().hint(GuiKeys.VOTE_HINT.text()).build();
    }
}
