package de.etiennebader.civ.server.credit;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.credit.Loan;
import de.etiennebader.civ.domain.credit.LoanId;
import de.etiennebader.civ.domain.credit.LoanOffer;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.server.town.TownRoles;
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

    /** Ein Ergebnis, das entweder den handelnden Akteur oder die Ablehnung trägt. */
    private record Acting(Optional<ActorRef> actor, Reply failure) {
    }

    /**
     * Wer handelt: der Spieler selbst oder, bei {@code as}, die gleichnamige Firma bzw. Gemeinde, sofern der Spieler
     * für deren Kasse zeichnen darf (Firmenleitung, Bürgermeister oder Schatzmeister).
     */
    private Acting acting(Caller caller, String as) {
        if (as == null || as.isBlank()) {
            return new Acting(Optional.of(caller.player()), null);
        }
        Optional<PolityView> polity = queries.polityNamed(as.strip());
        if (polity.isEmpty()) {
            return new Acting(Optional.empty(), Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, as.strip())));
        }
        try {
            if (polity.get().kind() == PolityKind.COMPANY) {
                app.companies().requireHead(caller.player(), polity.get().id(), PolityKind.COMPANY);
            } else if (!TownRoles.mayManageTreasury(polity.get(), caller.player())) {
                return new Acting(Optional.empty(),
                        Reply.failure(Message.of(MessageKey.ONLY_MAYOR, polity.get().name().value())));
            }
        } catch (DomainException e) {
            return new Acting(Optional.empty(), Reply.failure(texts.error(e)));
        }
        return new Acting(Optional.of(polity.get().asActor()), null);
    }

    private Reply asActor(Caller caller, String as, java.util.function.Function<ActorRef, Reply> action) {
        Acting acting = acting(caller, as);
        return acting.actor().map(action).orElse(acting.failure());
    }

    /** {@code /civ bank gruenden}: der Aufrufer (oder seine Firma/Gemeinde) wird als Bank lizenziert. */
    public Reply openBank(Caller caller, String as) {
        return asActor(caller, as, actor -> attempt(() -> {
            app.credit().licenseBank(actor, calendar.today());
            return Reply.success(Message.of(MessageKey.BANK_LICENSED));
        }));
    }

    /** {@code /civ bank schliessen} */
    public Reply closeBank(Caller caller, String as) {
        return asActor(caller, as, actor -> attempt(() -> {
            app.credit().closeBank(actor, calendar.today());
            return Reply.success(Message.of(MessageKey.BANK_CLOSED));
        }));
    }

    /** {@code /civ bank selbstbedienung an|aus}: Kreditnehmer dürfen ohne Angebot direkt bei der Bank leihen. */
    public Reply setSelfService(Caller caller, String as, boolean enabled) {
        return asActor(caller, as, actor -> attempt(() -> {
            app.credit().setSelfService(actor, enabled, calendar.today());
            return Reply.success(Message.of(enabled ? MessageKey.BANK_SELF_SERVICE_ON : MessageKey.BANK_SELF_SERVICE_OFF));
        }));
    }

    /** {@code /civ kredit zins <Bank> <Währung>}: der Zins, den der Aufrufer bei dieser Bank bekäme. */
    public Reply quote(Caller caller, String as, String lenderName, String currencyCode) {
        return asActor(caller, as, borrower -> quote(borrower, lenderName, currencyCode));
    }

    private Reply quote(ActorRef borrower, String lenderName, String currencyCode) {
        Optional<ActorRef> lender = lender(lenderName);
        if (lender.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, lenderName.strip()));
        }
        return attempt(() -> {
            int basisPoints = app.credit().quoteBasisPoints(lender.get(), borrower, id(currencyCode));
            return Reply.success(Message.of(MessageKey.LOAN_QUOTE, texts.actor(lender.get()), percent(basisPoints),
                    String.valueOf(queries.credit().scoreOf(borrower))));
        });
    }

    /** {@code /civ kredit nehmen <Bank> <Betrag> <Tage> [Währung]} */
    public Reply take(Caller caller, String as, String lenderName, String amountInput, long days,
                      String currencyCode) {
        return asActor(caller, as, borrower -> take(borrower, lenderName, amountInput, days, currencyCode));
    }

    private Reply take(ActorRef borrower, String lenderName, String amountInput, long days, String currencyCode) {
        Optional<ActorRef> lender = lender(lenderName);
        if (lender.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, lenderName.strip()));
        }
        return attempt(() -> {
            Money principal = Money.of(MoneyFormat.parse(amountInput), id(currencyCode));
            Loan loan = app.credit().takeSelfService(lender.get(), borrower, principal, days, calendar.today());
            return Reply.success(Message.of(MessageKey.LOAN_TAKEN, texts.money(principal), texts.actor(lender.get()),
                    percent(loan.rateBasisPoints()), texts.money(Money.of(loan.totalDue(), loan.currency())),
                    shortId(loan.id())));
        });
    }

    /** {@code /civ kredit zurueckzahlen <Kennung> <Betrag>} */
    public Reply repay(Caller caller, String as, String loanRef, String amountInput) {
        return asActor(caller, as, payer -> repay(payer, loanRef, amountInput));
    }

    private Reply repay(ActorRef payer, String loanRef, String amountInput) {
        Optional<Loan> loan = queries.credit().loansOfBorrower(payer).stream()
                .filter(found -> shortId(found.id()).equalsIgnoreCase(loanRef.strip())).findFirst();
        if (loan.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.LOAN_UNKNOWN, loanRef.strip()));
        }
        return attempt(() -> {
            Money paid = app.credit().repay(loan.get().id(), payer, MoneyFormat.parse(amountInput),
                    calendar.today());
            long left = queries.credit().loan(loan.get().id()).orElseThrow().outstanding();
            return Reply.success(Message.of(MessageKey.LOAN_REPAID, texts.money(paid),
                    texts.money(Money.of(left, paid.currency()))));
        });
    }

    /** {@code /civ kredit liste}: eigene Schulden und Forderungen sowie der Kreditscore. */
    public Reply overview(Caller caller, String as) {
        return asActor(caller, as, this::overview);
    }

    private Reply overview(ActorRef actor) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.CREDIT_SCORE, String.valueOf(queries.credit().scoreOf(actor))));
        List<Loan> owed = queries.credit().loansOfBorrower(actor);
        List<Loan> lent = queries.credit().loansOfLender(actor);
        List<LoanOffer> offers = queries.credit().offers().stream()
                .filter(offer -> offer.lender().equals(actor) || offer.borrower().equals(actor)).toList();
        if (owed.isEmpty() && lent.isEmpty() && offers.isEmpty()) {
            lines.add(Message.of(MessageKey.LOAN_NONE));
        }
        owed.forEach(loan -> lines.add(entry(MessageKey.LOAN_OWED, loan, loan.lender())));
        lent.forEach(loan -> lines.add(entry(MessageKey.LOAN_LENT, loan, loan.borrower())));
        offers.forEach(offer -> lines.add(Message.of(
                offer.borrower().equals(actor) ? MessageKey.LOAN_OFFER_RECEIVED : MessageKey.LOAN_OFFER_SENT,
                shortId(offer.id()), texts.actor(offer.borrower().equals(actor) ? offer.lender() : offer.borrower()),
                texts.money(Money.of(offer.principal(), offer.currency())), String.valueOf(offer.termDays()),
                String.valueOf(offer.expiresOn().value()))));
        return Reply.success(lines);
    }

    /** {@code /civ kredit anbieten <Kreditnehmer> <Betrag> <Tage> [Währung]} */
    public Reply offer(Caller caller, String as, String borrowerName, String amountInput, long days,
                       String currencyCode) {
        return asActor(caller, as, lender -> {
            Optional<ActorRef> borrower = lender(borrowerName);
            if (borrower.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, borrowerName.strip()));
            }
            return attempt(() -> {
                Money principal = Money.of(MoneyFormat.parse(amountInput), id(currencyCode));
                LoanOffer offer = app.credit().offerLoan(lender, borrower.get(), principal, days, calendar.today());
                return Reply.success(Message.of(MessageKey.LOAN_OFFERED, texts.money(principal),
                        texts.actor(borrower.get()), shortId(offer.id()), String.valueOf(offer.expiresOn().value())));
            });
        });
    }

    /** {@code /civ kredit annehmen <Kennung>} */
    public Reply accept(Caller caller, String as, String offerRef) {
        return asActor(caller, as, borrower -> {
            Optional<LoanOffer> offer = findOffer(borrower, offerRef, true);
            if (offer.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.LOAN_UNKNOWN, offerRef.strip()));
            }
            return attempt(() -> {
                Loan loan = app.credit().acceptOffer(offer.get().id(), borrower, calendar.today());
                return Reply.success(Message.of(MessageKey.LOAN_TAKEN,
                        texts.money(Money.of(loan.principal(), loan.currency())), texts.actor(loan.lender()),
                        percent(loan.rateBasisPoints()), texts.money(Money.of(loan.totalDue(), loan.currency())),
                        shortId(loan.id())));
            });
        });
    }

    /** {@code /civ kredit ablehnen <Kennung>}: Kreditnehmer lehnt ab, Kreditgeber zieht zurück. */
    public Reply decline(Caller caller, String as, String offerRef) {
        return asActor(caller, as, actor -> {
            Optional<LoanOffer> offer = findOffer(actor, offerRef, false);
            if (offer.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.LOAN_UNKNOWN, offerRef.strip()));
            }
            return attempt(() -> {
                app.credit().cancelOffer(offer.get().id(), actor, calendar.today());
                return Reply.success(Message.of(MessageKey.LOAN_OFFER_CLOSED, shortId(offer.get().id())));
            });
        });
    }

    private Optional<LoanOffer> findOffer(ActorRef actor, String ref, boolean asBorrowerOnly) {
        return queries.credit().offers().stream()
                .filter(offer -> offer.borrower().equals(actor) || !asBorrowerOnly && offer.lender().equals(actor))
                .filter(offer -> shortId(offer.id()).equalsIgnoreCase(ref.strip())).findFirst();
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
