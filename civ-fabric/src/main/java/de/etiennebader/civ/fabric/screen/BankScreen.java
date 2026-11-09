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
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Die Bank: Smaragde einzahlen und abheben, Kontostand ansehen.
 */
public final class BankScreen extends MenuScreen {

    private static final int[] AMOUNTS = {1, 16, 64};

    public BankScreen(CivServer civ, ServerPlayer player) {
        super(civ, player, Optional.empty());
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
        canvas.set(22, Icon.of(Items.BARRIER).name(GuiKeys.BACK.text()).build(),
                click -> Screens.close(click.player()));
    }

    private void deposit(Click click, int wanted) {
        int taken = Emeralds.take(player, wanted);
        if (taken == 0) {
            report(click, Reply.failure(Message.of(MessageKey.BANK_NOTHING_TO_DEPOSIT)));
            return;
        }
        Reply reply = civ.bank().deposit(caller(), taken);
        if (!reply.success()) {
            Emeralds.give(player, taken);
        }
        report(click, reply);
        if (reply.success()) {
            Feedback.money(player);
        }
    }

    private void withdraw(Click click, int wanted) {
        long available = civ.bank().withdrawableEmeralds(Players.ref(player));
        int amount = (int) Math.min(wanted, Math.max(available, 1));
        Reply reply = civ.bank().withdraw(caller(), amount);
        if (reply.success()) {
            Emeralds.give(player, amount);
            Feedback.money(player);
        }
        report(click, reply);
    }
}
