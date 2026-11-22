package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.credit.CreditScore;
import de.etiennebader.civ.domain.credit.Loan;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;

import java.util.List;
import java.util.Objects;

/**
 * Dorfbewohner und Kredite: Wer knapp bei Kasse ist und einen guten Kreditscore hat, nimmt bei einer Bank mit
 * Selbstbedienung (in der Standardwährung seines Gebiets) einen kleinen Kredit auf und zahlt ihn zurück, sobald das
 * Geld reicht. Kann er nicht zahlen, fällt der Kredit aus und der Score sinkt – dann bekommt er so bald keinen neuen.
 */
public final class VillagerBorrowing {

    /** Unter diesem Guthaben (kleinste Einheit) leiht sich ein Dorfbewohner Geld. */
    public static final long LOW_BALANCE = 200;
    /** Kreditbetrag in kleinster Einheit. */
    public static final long LOAN_AMOUNT = 500;
    public static final long TERM_DAYS = 30;
    /** Mindest-Score, ab dem ein Dorfbewohner sich traut, einen Kredit aufzunehmen. */
    public static final int MIN_SCORE = CreditScore.START - CreditScore.DEFAULT_PENALTY / 2;

    private final WorldQueries queries;
    private final CreditUseCases credit;
    private final CurrencyUseCases currencies;

    public VillagerBorrowing(WorldQueries queries, CreditUseCases credit, CurrencyUseCases currencies) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.credit = Objects.requireNonNull(credit, "credit");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
    }

    /** Tagesabschluss für alle Dorfbewohner aller Gebietsgemeinden. */
    public void execute(Day day) {
        List<ActorRef> banks = queries.credit().selfServiceBanks().stream().toList();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            CurrencyId currency = currencies.defaultCurrencyOf(town);
            town.members().stream().map(membership -> membership.member()).filter(PersonRef.class::isInstance)
                    .forEach(villager -> handle(villager, currency, banks, day));
        }
    }

    private void handle(ActorRef villager, CurrencyId currency, List<ActorRef> banks, Day day) {
        long balance = queries.ledger().balanceOf(villager, currency).amount();
        List<Loan> open = queries.credit().loansOfBorrower(villager).stream()
                .filter(loan -> loan.status() != de.etiennebader.civ.domain.credit.LoanStatus.REPAID).toList();
        try {
            for (Loan loan : open) {
                if (loan.currency().equals(currency) && balance >= loan.outstanding() + LOW_BALANCE) {
                    credit.repay(loan.id(), villager, loan.outstanding(), day);
                    balance -= loan.outstanding();
                }
            }
            boolean indebted = !queries.credit().loansOfBorrower(villager).stream()
                    .filter(loan -> loan.status() != de.etiennebader.civ.domain.credit.LoanStatus.REPAID).toList()
                    .isEmpty();
            if (!indebted && balance < LOW_BALANCE && queries.credit().scoreOf(villager) >= MIN_SCORE) {
                for (ActorRef bank : banks) {
                    if (queries.ledger().balanceOf(bank, currency).amount() >= LOAN_AMOUNT) {
                        credit.takeSelfService(bank, villager, Money.of(LOAN_AMOUNT, currency), TERM_DAYS, day);
                        return;
                    }
                }
            }
        } catch (DomainException | ArithmeticException e) {
            // Ablehnung (Gesetz, Zinsdeckel, fehlendes Geld) ist für Dorfbewohner kein Fehler: sie versuchen es später.
        }
    }
}
