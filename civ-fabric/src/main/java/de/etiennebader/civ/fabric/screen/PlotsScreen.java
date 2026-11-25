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
    /** Die Firma, für die gekauft und der übergeben wird; leer: für den Spieler selbst. */
    private Optional<String> company = Optional.empty();

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
                if (plot.heldByViewer() && !plot.townLand() && click.right()) {
                    Feedback.click(click.player());
                    de.etiennebader.civ.fabric.gui.Screens.open(click.player(),
                            new PlotRightsScreen(civ, click.player(), plot.town(), plot.id(), plot.townName(), this));
                    return;
                }
                if (click.shift() && company.isPresent() && plot.heldByViewer() && !plot.townLand()) {
                    Reply handed = civ.plots().transferToCompany(caller(), plot.town(), plot.id(), company.get());
                    report(click, handed);
                    return;
                }
                if (!click.shift() || plot.askingPrice().isEmpty() || plot.heldByViewer()) {
                    return;
                }
                Reply reply = company.isPresent()
                        ? civ.plots().buyForCompany(caller(), plot.town(), plot.id(), company.get())
                        : civ.plots().buy(caller(), plot.town(), plot.id());
                report(click, reply);
                if (reply.success()) {
                    Feedback.fanfare(click.player());
                }
            });
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        List<String> companies = civ.plots().companiesHeadedBy(Players.ref(player));
        if (company.filter(name -> !companies.contains(name)).isPresent()) {
            company = Optional.empty();
        }
        if (!companies.isEmpty()) {
            canvas.set(47, Icon.of(Items.EMERALD_BLOCK)
                    .name(company.map(name -> GuiKeys.PLOTS_COMPANY.text(name))
                            .orElseGet(() -> GuiKeys.PLOTS_COMPANY_SELF.text()), Icon.TITLE)
                    .hint(GuiKeys.PLOTS_COMPANY_HINT.text()).build(), click -> {
                        int next = company.map(companies::indexOf).orElse(-1) + 1;
                        company = next >= companies.size() ? Optional.empty() : Optional.of(companies.get(next));
                    });
        }
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
                icon.blank().hint(company.isPresent() ? GuiKeys.PLOT_COMPANY_BUY_HINT.text()
                        : GuiKeys.PLOT_BUY_HINT.text());
            }
        } else {
            icon.hint(GuiKeys.PLOT_ENTRY_NOT_OFFERED.text());
        }
        if (plot.heldByViewer() && !plot.townLand()) {
            icon.hint(GuiKeys.PLOT_RIGHTS_EDIT_HINT.text());
            if (company.isPresent()) {
                icon.hint(GuiKeys.PLOT_COMPANY_TRANSFER_HINT.text());
            }
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
            case EDUCATION -> Items.BOOKSHELF;
            case RELIGIOUS -> Items.LECTERN;
        };
    }
}
