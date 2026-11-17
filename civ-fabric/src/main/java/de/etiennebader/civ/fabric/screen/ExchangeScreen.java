package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Click;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.company.CompanyService;
import de.etiennebader.civ.server.company.HoldingView;
import de.etiennebader.civ.server.company.ListingView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Die Börse: alle notierten Firmen mit Kurs. Klick kauft eine Aktie (Schleichen: zehn), Rechtsklick verkauft; ohne
 * Limit bis 10 % über bzw. unter dem letzten Kurs.
 */
public final class ExchangeScreen extends MenuScreen {

    private static final int SLOTS = 36;

    public ExchangeScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.EXCHANGE_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<ListingView> listings = civ.companies().listings();
        List<HoldingView> holdings = civ.companies().holdingsOf(Players.ref(player));
        for (int index = 0; index < Math.min(SLOTS, listings.size()); index++) {
            ListingView listing = listings.get(index);
            long own = holdings.stream().filter(holding -> holding.companyId().equals(listing.id()))
                    .mapToLong(HoldingView::shares).sum();
            String change = String.format(Locale.GERMANY, "%+.1f", listing.change());
            Icon icon = Icon.of(listing.change() >= 0 ? Items.EMERALD : Items.REDSTONE)
                    .name(Icon.literal(listing.name(), listing.change() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .line(GuiKeys.EXCHANGE_PRICE.text(listing.price(), change), Icon.TITLE)
                    .line(GuiKeys.EXCHANGE_BOOK.text(listing.bookValue(), listing.marketCap()), Icon.INFO);
            if (own > 0) {
                icon.line(GuiKeys.EXCHANGE_OWN.text(own), Icon.GOOD).glint();
            }
            canvas.set(index, icon.blank().hint(GuiKeys.EXCHANGE_BUY_HINT.text())
                    .hint(GuiKeys.EXCHANGE_SELL_HINT.text()).build(), click -> trade(click, listing));
        }
        if (listings.isEmpty()) {
            canvas.set(22, Icon.of(Items.PAPER).name(Message.of(MessageKey.COMPANIES_NONE), Icon.INFO).build());
        }
        List<de.etiennebader.civ.domain.polity.PolityView> led = civ.companies().led(Players.ref(player));
        for (int index = 0; index < Math.min(3, led.size()); index++) {
            String name = led.get(index).name().value();
            canvas.set(36 + index, Icon.of(Items.CRAFTING_TABLE).name(Icon.literal(name, ChatFormatting.GOLD))
                    .hint(GuiKeys.COMPANY_OPEN_HINT.text()).build(), click -> {
                        de.etiennebader.civ.fabric.Feedback.click(click.player());
                        de.etiennebader.civ.fabric.gui.Screens.open(click.player(),
                                new CompanyScreen(civ, click.player(), name, Optional.of(this)));
                    });
        }
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        long value = holdings.stream().mapToLong(HoldingView::valueUnits).sum();
        Icon depot = Icon.of(Items.CHEST).name(GuiKeys.EXCHANGE_DEPOT.text(), Icon.TITLE)
                .line(GuiKeys.EXCHANGE_DEPOT_VALUE.text(CompanyService.money(value)), Icon.INFO);
        holdings.stream().limit(8).forEach(holding -> depot.line(Icon.literal(holding.company() + ": "
                + holding.shares() + " × " + holding.price(), ChatFormatting.GRAY)));
        canvas.set(40, depot.build(), click -> {
            Feedback.click(click.player());
            Feedback.reply(click.player(), civ.companies().depot(caller()));
        });
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        canvas.set(49, Icon.of(Items.KNOWLEDGE_BOOK).name(GuiKeys.EXCHANGE_TITLE.text(), Icon.TITLE)
                .line(Message.of(MessageKey.HELP_MARKET_PRESS), Icon.INFO).build());
    }

    private void trade(Click click, ListingView listing) {
        Feedback.click(click.player());
        long quantity = click.shift() ? 10 : 1;
        report(click, civ.companies().trade(caller(), click.right() ? Side.SELL : Side.BUY, listing.name(), quantity,
                Optional.empty()));
    }
}
