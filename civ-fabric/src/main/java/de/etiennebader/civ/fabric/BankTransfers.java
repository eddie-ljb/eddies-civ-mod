package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.economy.Currency;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Ein- und Auszahlungen am Bankschalter zwischen Inventar und Konto, für Smaragde und für die Bargeld-Items eigener
 * Währungen. Gemeinsam genutzt von {@code /civ einzahlen|abheben} und dem Bank-Bildschirm.
 */
public final class BankTransfers {

    private BankTransfers() {
    }

    public static Reply deposit(CivServer civ, ServerPlayer player, int wanted) {
        int taken = Emeralds.take(player, Math.min(wanted, Emeralds.count(player)));
        if (taken == 0) {
            return Reply.failure(Message.of(MessageKey.BANK_NOTHING_TO_DEPOSIT));
        }
        Reply reply = civ.bank().deposit(Players.caller(player), taken);
        if (reply.success()) {
            Feedback.money(player);
        } else {
            Emeralds.give(player, taken);
        }
        return reply;
    }

    public static Reply withdraw(CivServer civ, ServerPlayer player, int amount) {
        Reply reply = civ.bank().withdraw(Players.caller(player), amount);
        if (reply.success()) {
            Emeralds.give(player, amount);
            Feedback.money(player);
        }
        return reply;
    }

    /** Auszahlung in einer eigenen Währung: gekennzeichnete Items gegen Abbuchung. */
    public static Reply withdrawIn(CivServer civ, ServerPlayer player, int count, String code) {
        Optional<Currency> currency = civ.currencies().find(code);
        if (currency.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BANK_CURRENCY_UNSUPPORTED, code.strip()));
        }
        if (currency.get().id().equals(CurrencyId.EMERALD)) {
            return withdraw(civ, player, count);
        }
        Optional<String> item = currency.get().itemId().filter(CurrencyItems::exists);
        if (item.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BANK_CURRENCY_UNSUPPORTED, currency.get().id().code()));
        }
        Reply reply = civ.bank().withdrawIn(Players.caller(player), count, currency.get().id());
        if (reply.success()) {
            CurrencyItems.give(player, item.get(), currency.get().id().code(), currency.get().name(), count);
            Feedback.money(player);
        }
        return reply;
    }

    /** Einzahlung von Bank-Items einer eigenen Währung. */
    public static Reply depositIn(CivServer civ, ServerPlayer player, int wanted, String code) {
        Optional<Currency> currency = civ.currencies().find(code);
        if (currency.isEmpty() || currency.get().itemId().isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BANK_CURRENCY_UNSUPPORTED, code.strip()));
        }
        if (currency.get().id().equals(CurrencyId.EMERALD)) {
            return deposit(civ, player, wanted);
        }
        String item = currency.get().itemId().get();
        String id = currency.get().id().code();
        int taken = CurrencyItems.take(player, item, id, wanted);
        if (taken == 0) {
            return Reply.failure(Message.of(MessageKey.BANK_NOTHING_TO_DEPOSIT));
        }
        Reply reply = civ.bank().depositIn(Players.caller(player), taken, currency.get().id());
        if (reply.success()) {
            Feedback.money(player);
        } else {
            CurrencyItems.give(player, item, id, currency.get().name(), taken);
        }
        return reply;
    }
}
