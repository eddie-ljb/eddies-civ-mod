package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.market.Quote;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Goods;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Click;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.market.ItemTraits;
import de.etiennebader.civ.server.market.MarketOffer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Der Servermarkt: oben das Lager mit Kaufpreisen, verkauft wird per Klick auf Stapel im eigenen Inventar.
 */
public final class MarketScreen extends MenuScreen {

    private static final int PER_PAGE = 45;
    private static final int MAX_OFFERS = 1_000;
    private static final int BULK = 16;

    private int page;

    public MarketScreen(CivServer civ, ServerPlayer player) {
        super(civ, player, Optional.empty());
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.MARKET_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<MarketOffer> offers = civ.market().offers(MAX_OFFERS).stream()
                .filter(offer -> Goods.item(offer.commodity()).isPresent())
                .toList();
        int pages = Math.max(1, (offers.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);

        if (offers.isEmpty()) {
            canvas.set(22, Icon.of(Items.CHEST).name(GuiKeys.MARKET_EMPTY.text(), Icon.INFO).build());
        }
        for (int index = 0; index < PER_PAGE && page * PER_PAGE + index < offers.size(); index++) {
            MarketOffer offer = offers.get(page * PER_PAGE + index);
            Item item = Goods.item(offer.commodity()).orElseThrow();
            ItemTraits traits = Goods.traits(new ItemStack(item));
            Optional<Quote> quote = civ.market().quote(traits);
            if (quote.isEmpty()) {
                continue;
            }
            canvas.set(index, Icon.of(item).count((int) Math.min(offer.stock(), 64))
                    .line(GuiKeys.MARKET_BUY_PRICE.text(money(quote.get().buyPrice())), Icon.TITLE)
                    .line(GuiKeys.MARKET_SELL_PRICE.text(money(quote.get().sellPrice())), Icon.INFO)
                    .line(GuiKeys.MARKET_STOCK.text(offer.stock()), Icon.INFO)
                    .blank()
                    .hint(GuiKeys.MARKET_HELP_BUY.text()).build(), click -> buy(click, item, traits));
        }

        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.brown()).hidden().build());
        backButton(canvas);
        if (page > 0) {
            canvas.set(46, Icon.of(Items.ARROW).name(GuiKeys.PAGE_PREVIOUS.text()).build(), click -> {
                page--;
                Feedback.click(click.player());
            });
        }
        canvas.set(48, Icon.of(Items.GOLD_INGOT)
                .name(GuiKeys.BANK_BALANCE.text(money(civ.bank().balanceOf(Players.ref(player))))).build());
        canvas.set(49, Icon.of(Items.KNOWLEDGE_BOOK).name(GuiKeys.MARKET_HELP.text())
                .line(GuiKeys.MARKET_HELP_SELL.text())
                .line(GuiKeys.MARKET_HELP_SELL_ONE.text())
                .line(GuiKeys.MARKET_HELP_BUY.text())
                .blank()
                .hint(GuiKeys.PAGE.text(page + 1, pages)).build());
        canvas.set(50, Icon.of(Items.CLOCK)
                .name(GuiKeys.MARKET_LEVEL.text(civ.market().priceLevelPercent())).build());
        if (page < pages - 1) {
            canvas.set(52, Icon.of(Items.ARROW).name(GuiKeys.PAGE_NEXT.text()).build(), click -> {
                page++;
                Feedback.click(click.player());
            });
        }
    }

    private void buy(Click click, Item item, ItemTraits traits) {
        int quantity = click.shift() ? BULK : 1;
        Reply reply = civ.market().buy(caller(), traits, quantity);
        if (reply.success()) {
            int remaining = quantity;
            while (remaining > 0) {
                int stack = Math.min(remaining, item.getDefaultMaxStackSize());
                player.getInventory().placeItemBackInInventory(new ItemStack(item, stack), Prediction.SERVER_ONLY);
                remaining -= stack;
            }
            Feedback.money(player);
        }
        report(click, reply);
    }

    @Override
    public boolean clickedInventory(Click click, Slot slot) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return false;
        }
        int quantity = click.right() ? 1 : stack.getCount();
        Reply reply = civ.market().sell(caller(), Goods.traits(stack), quantity);
        if (reply.success()) {
            slot.remove(quantity);
            Feedback.money(player);
        }
        report(click, reply);
        return reply.success();
    }
}
