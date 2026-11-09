package de.etiennebader.civ.server.bank;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawEmeralds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.town.TownRoles;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Die Bank aus Spielersicht. Smaragde im Inventar bewegt der Aufrufer im Spiel: vor
 * {@link #deposit} nimmt er sie dem Spieler ab, nach einem erfolgreichen {@link #withdraw} gibt er sie ihm.
 *
 * <p>Ein Smaragd entspricht {@value MoneyFormat#UNITS_PER_EMERALD} Einheiten Buchgeld.
 */
public final class BankService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final CurrencyId currency;
    private final PlayerDirectory players;
    private final Texts texts;

    public BankService(CivApplication app, Calendar calendar, CurrencyId currency, PlayerDirectory players, Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    public Money balanceOf(ActorRef account) {
        return queries.ledger().balanceOf(account, currency);
    }

    /** So viele ganze Smaragde kann der Spieler höchstens abheben. */
    public long withdrawableEmeralds(ActorRef account) {
        return balanceOf(account).amount() / MoneyFormat.UNITS_PER_EMERALD;
    }

    /** {@code /civ konto} */
    public Reply balance(Caller caller) {
        return Reply.success(Message.of(MessageKey.BANK_BALANCE, texts.money(balanceOf(caller.player()))));
    }

    /** Schreibt Smaragde gut, die der Aufrufer dem Spieler bereits abgenommen hat. */
    public Reply deposit(Caller caller, int emeralds) {
        if (emeralds < 1) {
            return Reply.failure(Message.of(MessageKey.BANK_NOTHING_TO_DEPOSIT));
        }
        return attempt(() -> {
            Money amount = Money.of(MoneyFormat.emeralds(emeralds), currency);
            app.depositEmeralds().execute(new DepositEmeralds.Command(caller.player(), amount, calendar.today()));
            return Reply.success(Message.of(MessageKey.BANK_DEPOSITED, texts.money(amount),
                    texts.money(balanceOf(caller.player()))));
        });
    }

    /** Bucht Smaragde ab; bei Erfolg muss der Aufrufer sie dem Spieler geben. */
    public Reply withdraw(Caller caller, int emeralds) {
        if (emeralds < 1) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, String.valueOf(emeralds)));
        }
        return attempt(() -> {
            Money amount = Money.of(MoneyFormat.emeralds(emeralds), currency);
            app.withdrawEmeralds().execute(new WithdrawEmeralds.Command(caller.player(), amount, calendar.today()));
            return Reply.success(Message.of(MessageKey.BANK_WITHDRAWN, texts.money(amount),
                    texts.money(balanceOf(caller.player()))));
        });
    }

    /** {@code /civ ueberweisen <Spieler|Gemeinde> <Betrag>} */
    public Reply transfer(Caller caller, String recipientName, String amountInput) {
        Optional<ActorRef> recipient = recipient(recipientName);
        if (recipient.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, recipientName.strip()));
        }
        if (recipient.get().equals(caller.player())) {
            return Reply.failure(Message.of(MessageKey.TRANSFER_TO_SELF));
        }
        return withAmount(amountInput, amount -> attempt(() -> {
            app.transferFunds().execute(new TransferFunds.Command(caller.player(), recipient.get(), amount,
                    TransferReason.GRANT, calendar.today()));
            return Reply.success(Message.of(MessageKey.TRANSFERRED, texts.money(amount), texts.actor(recipient.get())));
        }));
    }

    /** Zahlt vom eigenen Konto in die Kasse der eigenen Gemeinde ein. */
    public Reply payIntoTreasury(Caller caller, String amountInput) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return withAmount(amountInput, amount -> attempt(() -> {
            app.transferFunds().execute(new TransferFunds.Command(caller.player(), town.get().asActor(), amount,
                    TransferReason.GRANT, calendar.today()));
            return Reply.success(Message.of(MessageKey.TREASURY_PAID_IN, texts.money(amount), town.get().name().value(),
                    texts.money(balanceOf(town.get().asActor()))));
        }));
    }

    /** Der Bürgermeister nimmt Geld aus der Gemeindekasse auf sein Konto. */
    public Reply takeFromTreasury(Caller caller, String amountInput) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        if (!TownRoles.isMayor(town.get(), caller.player())) {
            return Reply.failure(Message.of(MessageKey.ONLY_MAYOR, town.get().name().value()));
        }
        return withAmount(amountInput, amount -> attempt(() -> {
            app.transferFunds().execute(new TransferFunds.Command(town.get().asActor(), caller.player(), amount,
                    TransferReason.GRANT, calendar.today()));
            return Reply.success(Message.of(MessageKey.TREASURY_TAKEN, texts.money(amount), town.get().name().value(),
                    texts.money(balanceOf(town.get().asActor()))));
        }));
    }

    private Optional<ActorRef> recipient(String name) {
        Optional<ActorRef> player = players.findByName(name.strip()).map(ActorRef.class::cast);
        if (player.isPresent()) {
            return player;
        }
        return queries.polityNamed(name).map(PolityView::asActor);
    }

    private Reply withAmount(String input, Function<Money, Reply> action) {
        long units;
        try {
            units = MoneyFormat.parse(input);
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, input.strip()));
        }
        return action.apply(Money.of(units, currency));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
