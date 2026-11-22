package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Emeralds;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Click;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import de.etiennebader.civ.domain.economy.Currency;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.fabric.CurrencyItems;
import de.etiennebader.civ.fabric.BankTransfers;

import java.util.Optional;

/**
 * Die Bank: Smaragde einzahlen und abheben, Kontostand ansehen.
 */
public final class BankScreen extends MenuScreen {

    private static final int[] AMOUNTS = {1, 16, 64};

    private final CurrencyId payout;

    public BankScreen(CivServer civ, ServerPlayer player) {
        this(civ, player, CurrencyId.EMERALD);
    }

    /** Mit gewählter Auszahlungswährung (Bargeld-Item der Währung; Smaragd ist der Standard). */
    private BankScreen(CivServer civ, ServerPlayer player, CurrencyId payout) {
        super(civ, player, Optional.empty());
        this.payout = payout;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.BANK_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.lime()).hidden().build());
        canvas.fillRow(2, Icon.of(Items.STAINED_GLASS_PANE.lime()).hidden().build());
        long withdrawable = civ.bank().withdrawableEmeralds(Players.ref(player));
        int carried = Emeralds.count(player);
        canvas.set(4, Icon.of(Items.GOLD_BLOCK)
                .name(GuiKeys.BANK_BALANCE.text(money(civ.bank().balanceOf(Players.ref(player)))))
                .line(GuiKeys.BANK_WITHDRAW_HINT.text(withdrawable))
                .blank()
                .hint(GuiKeys.BANK_TRANSFER_HINT.text()).glint().build());

        for (int index = 0; index < AMOUNTS.length; index++) {
            int amount = AMOUNTS[index];
            canvas.set(10 + index, Icon.of(Items.EMERALD).count(amount)
                    .name(GuiKeys.BANK_DEPOSIT.text(amount), Icon.GOOD).build(), click -> deposit(click, amount));
            canvas.set(14 + index, Icon.of(Items.GOLD_INGOT).count(amount)
                    .name(GuiKeys.BANK_WITHDRAW.text(amount), Icon.TITLE).build(), click -> withdraw(click, amount));
        }
        canvas.set(13, Icon.of(Items.EMERALD_BLOCK).name(GuiKeys.BANK_DEPOSIT_ALL.text(), Icon.GOOD)
                .line(GuiKeys.BANK_DEPOSIT.text(carried)).build(), click -> deposit(click, Emeralds.count(player)));
        canvas.set(2, payoutIcon(), click -> Screens.open(click.player(), new BankScreen(civ, player, nextPayout())));
        canvas.set(22, Icon.of(Items.BARRIER).name(GuiKeys.BACK.text()).build(),
                click -> Screens.close(click.player()));
    }

    private Currency payoutCurrency() {
        return civ.currencies().find(payout.code()).orElseGet(Currency::emerald);
    }

    /** Das Bargeld-Item der Auszahlungswährung als Symbol; ohne gültiges Item ein Goldklumpen. */
    private net.minecraft.world.item.ItemStack payoutIcon() {
        Currency currency = payoutCurrency();
        Item item = currency.itemId().flatMap(CurrencyItems::item).orElse(Items.GOLD_NUGGET);
        return Icon.of(item).name(GuiKeys.BANK_PAYOUT_CURRENCY.text(currency.name()))
                .line(GuiKeys.BANK_PAYOUT_HINT.text()).glint().build();
    }

    /** Die nächste Währung mit Bargeld-Item; nach der letzten wieder Smaragd. */
    private CurrencyId nextPayout() {
        java.util.List<Currency> options = civ.currencies().payoutCurrencies().stream()
                .filter(currency -> currency.id().equals(CurrencyId.EMERALD) || currency.itemId().filter(CurrencyItems::exists).isPresent())
                .toList();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id().equals(payout)) {
                return options.get((index + 1) % options.size()).id();
            }
        }
        return CurrencyId.EMERALD;
    }

    private void deposit(Click click, int wanted) {
        Reply reply = BankTransfers.depositIn(civ, player, wanted, payout.code());
        report(click, reply);
    }

    private void withdraw(Click click, int wanted) {
        int amount = payout.equals(CurrencyId.EMERALD)
                ? (int) Math.min(wanted, Math.max(civ.bank().withdrawableEmeralds(Players.ref(player)), 1))
                : (int) Math.min(wanted, Math.max(civ.bank().withdrawableIn(Players.ref(player), payout), 1));
        report(click, BankTransfers.withdrawIn(civ, player, amount, payout.code()));
    }
}
