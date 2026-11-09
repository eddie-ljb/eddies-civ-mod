package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.plot.PlotView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Alle Grundstücke einer Gemeinde, angebotene zuerst. Angebotene lassen sich per Shift-Klick kaufen.
 */
final class PlotsScreen extends MenuScreen {

    private static final int PER_PAGE = 45;

    private final PolityId town;
    private final String townName;
    private int page;

    PlotsScreen(CivServer civ, ServerPlayer player, PolityId town, String townName, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
        this.townName = townName;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.PLOTS_TITLE.text(townName));
    }

    @Override
    public void render(Canvas canvas) {
        List<PlotView> plots = civ.plots().plotsOf(town, Players.ref(player));
        int pages = Math.max(1, (plots.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        if (plots.isEmpty()) {
            canvas.set(22, Icon.of(Items.OAK_SIGN).name(GuiKeys.PLOTS_EMPTY.text(), Icon.INFO).build());
        }
        for (int index = 0; index < PER_PAGE && page * PER_PAGE + index < plots.size(); index++) {
            PlotView plot = plots.get(page * PER_PAGE + index);
            canvas.set(index, icon(plot), click -> {
                if (!click.shift() || plot.askingPrice().isEmpty() || plot.heldByViewer()) {
                    return;
                }
                Reply reply = civ.plots().buy(caller(), plot.town(), plot.id());
                report(click, reply);
                if (reply.success()) {
                    Feedback.fanfare(click.player());
                }
            });
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        if (page > 0) {
            canvas.set(46, Icon.of(Items.ARROW).name(GuiKeys.PAGE_PREVIOUS.text()).build(), click -> page--);
        }
        canvas.set(49, Icon.of(Items.KNOWLEDGE_BOOK).name(GuiKeys.PLOTS.text(plots.size()))
                .line(GuiKeys.PLOT_HOW_TO.text())
                .hint(GuiKeys.PAGE.text(page + 1, pages)).build());
        if (page < pages - 1) {
            canvas.set(52, Icon.of(Items.ARROW).name(GuiKeys.PAGE_NEXT.text()).build(), click -> page++);
        }
    }

    private net.minecraft.world.item.ItemStack icon(PlotView plot) {
        Area area = plot.area();
        Icon icon = Icon.of(item(plot.type()))
                .name(GuiKeys.PLOT_ENTRY.text(Message.of(TranslationKeys.plotType(plot.type()))),
                        plot.heldByViewer() ? Icon.GOOD : Icon.TITLE)
                .line(GuiKeys.PLOT_ENTRY_COORDS.text(area.minX(), area.minZ(), area.maxX(), area.maxZ(), area.size()))
                .line(GuiKeys.PLOT_ENTRY_HOLDER.text(plot.holder()));
        if (plot.askingPrice().isPresent()) {
            icon.line(GuiKeys.PLOT_ENTRY_PRICE.text(money(plot.askingPrice().get())), Icon.GOOD).glint();
            if (!plot.heldByViewer()) {
                icon.blank().hint(GuiKeys.PLOT_BUY_HINT.text());
            }
        } else {
            icon.hint(GuiKeys.PLOT_ENTRY_NOT_OFFERED.text());
        }
        return icon.build();
    }

    private static Item item(PlotType type) {
        return switch (type) {
            case RESIDENTIAL -> Items.OAK_DOOR;
            case COMMERCIAL -> Items.EMERALD;
            case INDUSTRIAL -> Items.FURNACE;
            case AGRICULTURAL -> Items.WHEAT;
            case PUBLIC -> Items.BELL;
            case EMBASSY -> Items.BANNER.white();
        };
    }
}
