package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Goods;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.ShopChests;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Click;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.shop.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Ein Laden aus Sicht eines Käufers: Ware, Preis, Lager und Knöpfe für 1, 16 und 64 Stück.
 */
public final class ShopScreen extends MenuScreen {

    private static final int[] AMOUNTS = {1, 16, 64};

    private final BlockPos pos;
    private final BlockPosition position;
    private final String ownerName;

    public ShopScreen(CivServer civ, ServerPlayer player, BlockPos pos, ShopService.ShopView shop) {
        super(civ, player, Optional.empty());
        this.pos = pos;
        this.position = shop.shop().position();
        this.ownerName = shop.owner();
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.SHOP_TITLE.text(ownerName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.orange()).hidden().build());
        canvas.fillRow(2, Icon.of(Items.STAINED_GLASS_PANE.orange()).hidden().build());
        Optional<ShopService.ShopView> view = civ.shops().shopAt(position);
        Optional<Container> container = ShopChests.containerAt(player.level(), pos);
        Optional<Item> item = view.flatMap(shop -> Goods.item(shop.shop().commodity()));
        if (view.isEmpty() || container.isEmpty() || item.isEmpty()) {
            canvas.set(13, Icon.of(Items.BARRIER).name(Message.of(MessageKey.SHOP_GONE), Icon.BAD).build());
            return;
        }
        int stock = ShopChests.stock(container.get(), item.get());
        canvas.set(4, Icon.of(item.get()).count(Math.max(1, Math.min(stock, 64)))
                .line(GuiKeys.SHOP_OWNER.text(view.get().owner()))
                .line(GuiKeys.SHOP_PRICE.text(money(view.get().shop().price())), Icon.TITLE)
                .line(GuiKeys.SHOP_STOCK.text(stock), stock > 0 ? Icon.GOOD : Icon.BAD)
                .line(GuiKeys.BANK_BALANCE.text(money(civ.bank().balanceOf(Players.ref(player)))))
                .build());
        for (int index = 0; index < AMOUNTS.length; index++) {
            int amount = AMOUNTS[index];
            boolean available = stock >= amount;
            canvas.set(11 + index * 2, Icon.of(available ? Items.EMERALD : Items.DYE.gray()).count(amount)
                    .name(GuiKeys.SHOP_BUY.text(amount), available ? Icon.GOOD : Icon.HINT)
                    .line(GuiKeys.SHOP_BUY_PRICE.text(money(civ.shops().priceFor(view.get().shop(), amount))))
                    .build(), click -> buy(click, item.get(), amount));
        }
        canvas.set(22, Icon.of(Items.BARRIER).name(GuiKeys.BACK.text()).build(), click -> Screens.close(click.player()));
    }

    private void buy(Click click, Item item, int amount) {
        Optional<Container> container = ShopChests.containerAt(player.level(), pos);
        if (container.isEmpty()) {
            report(click, Reply.failure(Message.of(MessageKey.SHOP_GONE)));
            return;
        }
        int stock = ShopChests.stock(container.get(), item);
        Optional<ShopService.ShopView> before = civ.shops().shopAt(position);
        Reply reply = civ.shops().buy(caller(), position, Goods.traits(new ItemStack(item)), amount, stock);
        report(click, reply);
        if (reply.success()) {
            ShopChests.deliver(container.get(), item, amount, player);
            Feedback.money(player);
            before.ifPresent(shop -> notifyOwner(shop, item, amount));
        }
    }

    private void notifyOwner(ShopService.ShopView shop, Item item, int amount) {
        ServerPlayer owner = player.level().getServer().getPlayerList().getPlayer(shop.shop().owner().uuid());
        if (owner != null) {
            Components.showHint(owner, Message.of(MessageKey.SHOP_SOLD_NOTICE, Players.name(player), amount,
                    Message.of(item.getDescriptionId()), money(civ.shops().priceFor(shop.shop(), amount))), true);
            Feedback.money(owner);
        }
    }
}
