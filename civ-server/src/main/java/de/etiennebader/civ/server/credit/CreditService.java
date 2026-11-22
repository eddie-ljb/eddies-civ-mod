package de.etiennebader.civ.server.credit;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.credit.Loan;
import de.etiennebader.civ.domain.credit.LoanId;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Banken und Kredite aus Spielersicht. Kredite laufen in einer Währung, Beträge geben Spieler wie üblich in ganzen
 * Einheiten dieser Währung an.
 */
public final class CreditService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;

    public CreditService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    /** {@code /civ bank gruenden}: der Aufrufer wird als Bank lizenziert. */
    public Reply openBank(Caller caller) {
        return attempt(() -> {
            app.credit().licenseBank(caller.player(), calendar.today());
            return Reply.success(Message.of(MessageKey.BANK_LICENSED));
        });
    }

    /** {@code /civ bank schliessen} */
    public Reply closeBank(Caller caller) {
        return attempt(() -> {
            app.credit().closeBank(caller.player(), calendar.today());
            return Reply.success(Message.of(MessageKey.BANK_CLOSED));
        });
    }

    /** {@code /civ kredit zins <Bank> <Währung>}: der Zins, den der Aufrufer bei dieser Bank bekäme. */
    public Reply quote(Caller caller, String lenderName, String currencyCode) {
        Optional<ActorRef> lender = lender(lenderName);
        if (lender.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, lenderName.strip()));
        }
        return attempt(() -> {
            int basisPoints = app.credit().quoteBasisPoints(lender.get(), caller.player(), id(currencyCode));
            return Reply.success(Message.of(MessageKey.LOAN_QUOTE, texts.actor(lender.get()), percent(basisPoints),
                    String.valueOf(queries.credit().scoreOf(caller.player()))));
        });
    }

    /** {@code /civ kredit nehmen <Bank> <Betrag> <Tage> [Währung]} */
    public Reply take(Caller caller, String lenderName, String amountInput, long days, String currencyCode) {
        Optional<ActorRef> lender = lender(lenderName);
        if (lender.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, lenderName.strip()));
        }
        return attempt(() -> {
            Money principal = Money.of(MoneyFormat.parse(amountInput), id(currencyCode));
            Loan loan = app.credit().takeLoan(lender.get(), caller.player(), principal, days, calendar.today());
            return Reply.success(Message.of(MessageKey.LOAN_TAKEN, texts.money(principal), texts.actor(lender.get()),
                    percent(loan.rateBasisPoints()), texts.money(Money.of(loan.totalDue(), loan.currency())),
                    shortId(loan.id())));
        });
    }

    /** {@code /civ kredit zurueckzahlen <Kennung> <Betrag>} */
    public Reply repay(Caller caller, String loanRef, String amountInput) {
        Optional<Loan> loan = queries.credit().loansOfBorrower(caller.player()).stream()
                .filter(found -> shortId(found.id()).equalsIgnoreCase(loanRef.strip())).findFirst();
        if (loan.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.LOAN_UNKNOWN, loanRef.strip()));
        }
        return attempt(() -> {
            Money paid = app.credit().repay(loan.get().id(), caller.player(), MoneyFormat.parse(amountInput),
                    calendar.today());
            long left = queries.credit().loan(loan.get().id()).orElseThrow().outstanding();
            return Reply.success(Message.of(MessageKey.LOAN_REPAID, texts.money(paid),
                    texts.money(Money.of(left, paid.currency()))));
        });
    }

    /** {@code /civ kredit liste}: eigene Schulden und Forderungen sowie der Kreditscore. */
    public Reply overview(Caller caller) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.CREDIT_SCORE, String.valueOf(queries.credit().scoreOf(caller.player()))));
        List<Loan> owed = queries.credit().loansOfBorrower(caller.player());
        List<Loan> lent = queries.credit().loansOfLender(caller.player());
        if (owed.isEmpty() && lent.isEmpty()) {
            lines.add(Message.of(MessageKey.LOAN_NONE));
        }
        owed.forEach(loan -> lines.add(entry(MessageKey.LOAN_OWED, loan, loan.lender())));
        lent.forEach(loan -> lines.add(entry(MessageKey.LOAN_LENT, loan, loan.borrower())));
        return Reply.success(lines);
    }

    private Message entry(MessageKey key, Loan loan, ActorRef other) {
        return Message.of(key, shortId(loan.id()), texts.actor(other),
                texts.money(Money.of(loan.outstanding(), loan.currency())), String.valueOf(loan.dueOn().value()),
                loan.status().name().toLowerCase(Locale.ROOT));
    }

    /** Ein Spieler oder die Gemeinde/Firma dieses Namens. */
    private Optional<ActorRef> lender(String name) {
        Optional<ActorRef> player = players.findByName(name.strip()).map(ActorRef.class::cast);
        if (player.isPresent()) {
            return player;
        }
        return queries.polityNamed(name).map(polity -> polity.asActor());
    }

    private static String shortId(LoanId id) {
        return id.value().toString().substring(0, 8);
    }

    private static String percent(int basisPoints) {
        return basisPoints / 100 + "," + String.format("%02d", basisPoints % 100);
    }

    private static CurrencyId id(String code) {
        return code == null || code.isBlank() ? CurrencyId.EMERALD : new CurrencyId(code.strip().toUpperCase(Locale.ROOT));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException | ArithmeticException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, String.valueOf(e.getMessage())));
        }
    }
}
