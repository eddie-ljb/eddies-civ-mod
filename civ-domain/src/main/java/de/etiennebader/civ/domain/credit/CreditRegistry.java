package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Banken, Kreditscores und Kredite der Welt. Das Geld bewegt der Aufrufer über das Hauptbuch; dieses
 * Aggregat hält die Verträge fest und schreibt den Kreditscore fort: pünktliche Rückzahlung erhöht ihn,
 * Zahlungsausfall senkt ihn.
 */
public final class CreditRegistry extends AggregateRoot<CreditEvent> implements CreditView {

    public static final long MAX_TERM_DAYS = 3_650;
    public static final int MAX_RATE_BASIS_POINTS = 100_000;

    private final Set<ActorRef> banks = new LinkedHashSet<>();
    private final Map<ActorRef, Integer> scores = new LinkedHashMap<>();
    private final Map<LoanId, Loan> loans = new LinkedHashMap<>();

    private CreditRegistry() {
    }

    public static CreditRegistry empty() {
        return new CreditRegistry();
    }

    public static CreditRegistry restore(Collection<ActorRef> banks, Map<ActorRef, Integer> scores,
                                         Collection<Loan> loans) {
        CreditRegistry registry = new CreditRegistry();
        registry.banks.addAll(banks);
        scores.forEach((actor, score) -> registry.scores.put(actor, CreditScore.clamp(score)));
        loans.forEach(loan -> registry.loans.put(loan.id(), loan));
        return registry;
    }

    /** Lizenziert einen Akteur (Spieler, Gemeinde, Unternehmen) als Bank. */
    public void licenseBank(ActorRef bank, Day day) {
        if (!banks.add(Objects.requireNonNull(bank, "bank"))) {
            throw CreditException.alreadyABank(bank);
        }
        raise(new CreditEvent.BankLicensed(bank, day));
    }

    /** Schließt eine Bank; offene Kredite laufen weiter. */
    public void closeBank(ActorRef bank, Day day) {
        if (!banks.remove(Objects.requireNonNull(bank, "bank"))) {
            throw CreditException.notABank(bank);
        }
        raise(new CreditEvent.BankClosed(bank, day));
    }

    /**
     * Hält einen neuen Kredit fest. Zinsen ergeben sich aus Betrag, Zins und Laufzeit.
     *
     * @throws CreditException bei ungültigen Konditionen (Betrag, Laufzeit, Zins, Kreditgeber gleich Kreditnehmer)
     */
    public Loan issue(LoanId id, ActorRef lender, ActorRef borrower, CurrencyId currency, long principal,
                      int rateBasisPoints, long termDays, Day day) {
        Objects.requireNonNull(lender, "lender");
        Objects.requireNonNull(borrower, "borrower");
        if (principal <= 0 || termDays < 1 || termDays > MAX_TERM_DAYS || rateBasisPoints < 0
                || rateBasisPoints > MAX_RATE_BASIS_POINTS || lender.equals(borrower) || loans.containsKey(id)) {
            throw CreditException.invalidTerms();
        }
        long interest;
        try {
            interest = Loan.interestFor(principal, rateBasisPoints, termDays);
            Math.addExact(principal, interest);
        } catch (ArithmeticException e) {
            throw CreditException.invalidTerms();
        }
        Loan loan = new Loan(id, lender, borrower, currency, principal, rateBasisPoints, interest, day,
                day.plus(termDays), 0, LoanStatus.ACTIVE);
        loans.put(id, loan);
        raise(new CreditEvent.LoanIssued(id, day));
        return loan;
    }

    /**
     * Verbucht eine Rückzahlung des Kreditnehmers. Der Betrag wird auf die Restschuld gekürzt.
     *
     * @return der tatsächlich angerechnete Betrag
     */
    public long repay(LoanId id, ActorRef payer, long amount, Day day) {
        Loan loan = require(id);
        if (!loan.borrower().equals(payer)) {
            throw CreditException.notBorrower(id, payer);
        }
        if (loan.status() == LoanStatus.REPAID) {
            throw CreditException.notActive(id);
        }
        if (amount <= 0) {
            throw CreditException.invalidTerms();
        }
        long applied = Math.min(amount, loan.outstanding());
        long repaid = loan.repaid() + applied;
        boolean complete = repaid >= loan.totalDue();
        boolean onTime = loan.status() == LoanStatus.ACTIVE;
        loans.put(id, loan.withRepaid(repaid, complete ? LoanStatus.REPAID : loan.status()));
        if (complete && onTime) {
            adjustScore(payer, CreditScore.REPAID_BONUS);
        }
        raise(new CreditEvent.LoanRepaid(id, applied, complete, day));
        return applied;
    }

    /**
     * Markiert alle überfälligen Kredite als ausgefallen und senkt den Kreditscore der Schuldner.
     *
     * @return die neu ausgefallenen Kredite
     */
    public List<Loan> markOverdue(Day today) {
        List<Loan> defaulted = new ArrayList<>();
        for (Loan loan : List.copyOf(loans.values())) {
            if (loan.isActive() && today.isAfter(loan.dueOn())) {
                Loan updated = loan.withRepaid(loan.repaid(), LoanStatus.DEFAULTED);
                loans.put(loan.id(), updated);
                adjustScore(loan.borrower(), -CreditScore.DEFAULT_PENALTY);
                raise(new CreditEvent.LoanDefaulted(loan.id(), today));
                defaulted.add(updated);
            }
        }
        return defaulted;
    }

    private void adjustScore(ActorRef actor, int delta) {
        scores.put(actor, CreditScore.clamp(scoreOf(actor) + delta));
    }

    private Loan require(LoanId id) {
        Loan loan = loans.get(Objects.requireNonNull(id, "id"));
        if (loan == null) {
            throw CreditException.unknownLoan(id);
        }
        return loan;
    }

    @Override
    public boolean isBank(ActorRef actor) {
        return banks.contains(actor);
    }

    @Override
    public Set<ActorRef> banks() {
        return Set.copyOf(banks);
    }

    @Override
    public int scoreOf(ActorRef actor) {
        return scores.getOrDefault(Objects.requireNonNull(actor, "actor"), CreditScore.START);
    }

    @Override
    public Map<ActorRef, Integer> scores() {
        return Map.copyOf(scores);
    }

    @Override
    public Optional<Loan> loan(LoanId id) {
        return Optional.ofNullable(loans.get(id));
    }

    @Override
    public List<Loan> loans() {
        return List.copyOf(loans.values());
    }

    @Override
    public List<Loan> loansOfBorrower(ActorRef borrower) {
        return loans.values().stream().filter(loan -> loan.borrower().equals(borrower)).toList();
    }

    @Override
    public List<Loan> loansOfLender(ActorRef lender) {
        return loans.values().stream().filter(loan -> loan.lender().equals(lender)).toList();
    }
}
