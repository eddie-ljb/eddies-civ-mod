package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.campaign.PollView;
import de.etiennebader.civ.server.campaign.ResultView;
import de.etiennebader.civ.server.command.GuiKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Wahlen einer Gemeinde oder eines Bundes: oben Umfragen zu laufenden Wahlen (mit Sitzprognose beim Parlament), unten
 * die letzten Ergebnisse mit den Wahlbezirken.
 */
public final class ElectionsScreen extends MenuScreen {

    private static final int FIRST_POLL = 9;
    private static final int POLL_SLOTS = 18;
    private static final int FIRST_RESULT = 27;
    private static final int RESULT_SLOTS = 18;
    private static final int MAX_LINES = 8;

    private final PolityId polity;
    private final String initialName;

    public ElectionsScreen(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
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
        return Components.of(GuiKeys.ELECTIONS_TITLE.text(initialName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.lightBlue()).hidden().build());
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.lightBlue()).hidden().build());
        backButton(canvas);
        Optional<PolityView> found = civ.queries().polity(polity);
        if (found.isEmpty()) {
            return;
        }
        List<PollView> polls = civ.campaigns().polls(found.get());
        if (polls.isEmpty()) {
            canvas.set(13, Icon.of(Items.SPYGLASS).name(GuiKeys.ELECTIONS_POLL_NONE.text(), Icon.INFO).build());
        }
        for (int index = 0; index < Math.min(polls.size(), POLL_SLOTS); index++) {
            canvas.set(FIRST_POLL + index, poll(polls.get(index)));
        }
        List<ResultView> results = civ.campaigns().results(found.get());
        if (results.isEmpty()) {
            canvas.set(31, Icon.of(Items.PAPER).name(GuiKeys.ELECTIONS_NONE.text(), Icon.INFO).build());
        }
        for (int index = 0; index < Math.min(results.size(), RESULT_SLOTS); index++) {
            canvas.set(FIRST_RESULT + index, result(results.get(index)));
        }
    }

    private static ItemStack poll(PollView poll) {
        Icon icon = Icon.of(Items.SPYGLASS).name(GuiKeys.ELECTIONS_POLL.text(poll.officeName(), poll.closesOn()), Icon.TITLE)
                .line(GuiKeys.ELECTIONS_POLL_SAMPLE.text(poll.sample(), poll.undecided()), Icon.HINT);
        for (PollView.Entry entry : poll.entries().subList(0, Math.min(poll.entries().size(), MAX_LINES))) {
            icon.line(GuiKeys.ELECTIONS_POLL_LINE.text(entry.name(), entry.party().isEmpty() ? "" : " (" + entry.party() + ")",
                    entry.percent()), Icon.INFO);
        }
        if (!poll.lists().isEmpty()) {
            icon.line(GuiKeys.ELECTIONS_POLL_PROJECTION.text(poll.lists().stream()
                    .map(list -> list.name() + " " + list.seats()).collect(Collectors.joining(", "))), Icon.GOOD);
        }
        return icon.build();
    }

    private static ItemStack result(ResultView result) {
        Icon icon = Icon.of(Items.FILLED_MAP).name(GuiKeys.ELECTIONS_RESULT.text(result.officeName(), result.closedOn()),
                        Icon.TITLE)
                .line(GuiKeys.ELECTIONS_RESULT_WINNERS.text(result.winners().isEmpty() ? "-"
                        : String.join(", ", result.winners())), Icon.GOOD);
        for (ResultView.Line line : result.totals().subList(0, Math.min(result.totals().size(), MAX_LINES))) {
            icon.line(GuiKeys.ELECTIONS_RESULT_LINE.text(line.name(), CampaignService.votes(line.votes()), line.percent()),
                    Icon.INFO);
        }
        if (result.districts().size() > 1) {
            icon.blank();
            for (ResultView.District district : result.districts().subList(0,
                    Math.min(result.districts().size(), MAX_LINES))) {
                icon.line(GuiKeys.ELECTIONS_RESULT_DISTRICT.text(district.name(), district.leader()), Icon.HINT);
            }
        }
        return icon.build();
    }
}
