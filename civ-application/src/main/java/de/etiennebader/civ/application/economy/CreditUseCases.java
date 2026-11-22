package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.credit.CreditException;
import de.etiennebader.civ.domain.credit.CreditRegistry;
import de.etiennebader.civ.domain.credit.CreditScore;
import de.etiennebader.civ.domain.credit.Loan;
import de.etiennebader.civ.domain.credit.LoanId;
import de.etiennebader.civ.domain.economy.Currency;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;

import java.util.List;
import java.util.Objects;

/**
 * Banken und Kredite.
 *
 * <ul>
 *   <li>Banken sind lizenzierte Akteure (Spieler, Gemeinden, Unternehmen). Der Kreditnehmer nimmt bei einer Bank
 *       einen Kredit auf; das Geld kommt vom Konto der Bank.</li>
 *   <li>Der Jahreszins ist der Leitzins der Währung plus ein Aufschlag nach {@link CreditScore Kreditscore}.</li>
 *   <li>Die Zentralbank (Vorsitzender der Währung) leiht Banken Geld zum reinen Leitzins.</li>
 *   <li>Gesetze des Kreditnehmers können Kredite verbieten, den Zins begrenzen und einen Mindestscore verlangen.</li>
 * </ul>
 */
public final class CreditUseCases {

    private final Aggregates aggregates;
    private final WorldQueries queries;
    private final IdGenerator ids;

    public CreditUseCases(Aggregates aggregates, WorldQueries queries, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public void licenseBank(ActorRef bank, Day day) {
        CreditRegistry credit = aggregates.credit();
        credit.licenseBank(bank, day);
        aggregates.commit(credit);
    }

    public void closeBank(ActorRef bank, Day day) {
        CreditRegistry credit = aggregates.credit();
        credit.closeBank(bank, day);
        aggregates.commit(credit);
    }

    /** Der Jahreszins in Basispunkten, den der Kreditnehmer in dieser Währung gerade bekäme. */
    public int quoteBasisPoints(ActorRef lender, ActorRef borrower, CurrencyId currency) {
        Currency money = queries.currencies().require(currency);
        boolean centralBankLoan = isCentralBankLoan(lender, borrower, money);
        int score = centralBankLoan ? CreditScore.MAX : queries.credit().scoreOf(borrower);
        return CreditScore.rateBasisPoints(money.baseRatePercent(), score);
    }

    /**
     * Zahlt einen Kredit aus und hält den Vertrag fest.
     *
     * @throws CreditException                                           wenn der Kreditgeber weder Bank noch
     *                                                                   Zentralbank der Währung ist, das Recht des
     *                                                                   Kreditnehmers den Kredit verbietet oder
     *                                                                   Zins/Score nicht passen
     * @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Konto des Kreditgebers nicht reicht
     */
    public Loan takeLoan(ActorRef lender, ActorRef borrower, Money principal, long termDays, Day day) {
        Objects.requireNonNull(lender, "lender");
        Objects.requireNonNull(borrower, "borrower");
        Objects.requireNonNull(principal, "principal");
        Currency currency = queries.currencies().require(principal.currency());
        boolean centralBankLoan = isCentralBankLoan(lender, borrower, currency);
        if (!centralBankLoan && !queries.credit().isBank(lender)) {
            throw CreditException.notABank(lender);
        }
        if (!Jurisdiction.value(queries, borrower, StandardParameters.BANK_LENDING_ALLOWED)) {
            throw CreditException.lendingNotAllowed();
        }
        int score = queries.credit().scoreOf(borrower);
        long minScore = Jurisdiction.value(queries, borrower, StandardParameters.BANK_MIN_CREDIT_SCORE);
        if (!centralBankLoan && score < minScore) {
            throw CreditException.scoreTooLow(score, minScore);
        }
        int rate = quoteBasisPoints(lender, borrower, principal.currency());
        long maxPercent = Jurisdiction.value(queries, borrower, StandardParameters.BANK_MAX_INTEREST_PERCENT);
        if (!centralBankLoan && rate > maxPercent * 100) {
            throw CreditException.interestAboveLimit((rate + 99) / 100, maxPercent);
        }
        // Erst prüfen, dann buchen: Register und Hauptbuch sind gemeinsam genutzte Arbeitsstände, ein halb
        // ausgeführter Kredit würde sonst als Vertrag ohne Auszahlung zurückbleiben.
        Ledger ledger = aggregates.ledger();
        Money available = ledger.balanceOf(lender, principal.currency());
        if (principal.isPositive() && available.compareTo(principal) < 0) {
            throw new InsufficientFundsException(principal, available);
        }
        CreditRegistry credit = aggregates.credit();
        Loan loan = credit.issue(LoanId.generate(ids), lender, borrower, principal.currency(), principal.amount(),
                rate, termDays, day);
        ledger.transfer(lender, borrower, principal, TransferReason.LOAN, day);
        aggregates.commit(ledger);
        aggregates.commit(credit);
        return loan;
    }

    /**
     * Zahlt einen Teil oder den Rest eines Kredits zurück. Der Betrag wird auf die Restschuld gekürzt.
     *
     * @return der tatsächlich zurückgezahlte Betrag
     */
    public Money repay(LoanId id, ActorRef payer, long amount, Day day) {
        CreditRegistry credit = aggregates.credit();
        Loan loan = credit.loan(id).orElseThrow(() -> CreditException.unknownLoan(id));
        if (!loan.borrower().equals(payer)) {
            throw CreditException.notBorrower(id, payer);
        }
        long applied = Math.min(Math.max(amount, 0), loan.outstanding());
        if (applied <= 0) {
            throw CreditException.notActive(id);
        }
        Money payment = Money.of(applied, loan.currency());
        Ledger ledger = aggregates.ledger();
        ledger.transfer(payer, loan.lender(), payment, TransferReason.LOAN, day);
        credit.repay(id, payer, applied, day);
        aggregates.commit(ledger);
        aggregates.commit(credit);
        return payment;
    }

    /** Tagesabschluss: überfällige Kredite fallen aus, der Kreditscore der Schuldner sinkt. */
    public List<Loan> endOfDay(Day today) {
        CreditRegistry credit = aggregates.credit();
        List<Loan> defaulted = credit.markOverdue(today);
        aggregates.commit(credit);
        return defaulted;
    }

    private boolean isCentralBankLoan(ActorRef lender, ActorRef borrower, Currency currency) {
        return queries.credit().isBank(borrower) && currency.governor().filter(lender::equals).isPresent();
    }
}
