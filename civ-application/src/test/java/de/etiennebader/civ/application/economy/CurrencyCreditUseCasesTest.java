package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.credit.CreditScore;
import de.etiennebader.civ.domain.credit.Loan;
import de.etiennebader.civ.domain.credit.LoanStatus;
import de.etiennebader.civ.domain.economy.ExchangeRate;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyCreditUseCasesTest {

    private static final CurrencyId GOLD = new CurrencyId("GOLD");

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bank = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final LedgerView ledger = fixture.app.queries().ledger();
    private final CurrencyUseCases currencies = fixture.app.currencies();
    private final CreditUseCases credit = fixture.app.credit();

    private static ErrorCode codeOf(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }

    private void fund(PlayerRef who, long emeraldAmount) {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(who, emeralds(emeraldAmount), DAY));
    }

    @Test
    void currencyIsCreatedConvertedAndGoverned() {
        currencies.create(GOLD, "Gold", "G", new ExchangeRate(1, 10), alice, "minecraft:gold_ingot", DAY);
        assertEquals(Money.of(100, EMERALD), currencies.convert(Money.of(10, GOLD), EMERALD));
        assertEquals(ErrorCode.NOT_CURRENCY_GOVERNOR,
                codeOf(() -> currencies.changeRate(GOLD, ExchangeRate.PARITY, bob, DAY)));
        currencies.changeRate(GOLD, new ExchangeRate(1, 20), alice, DAY);
        assertEquals(Money.of(200, EMERALD), currencies.convert(Money.of(10, GOLD), EMERALD));
    }

    @Test
    void baseRateIsCappedByLaw() {
        PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
        currencies.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, null, DAY);
        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Zinsdeckel",
                List.of(ParameterValue.of(StandardParameters.CENTRAL_BANK_MAX_BASE_RATE_PERCENT, 5L)), DAY));
        assertEquals(ErrorCode.INTEREST_ABOVE_LIMIT, codeOf(() -> currencies.setBaseRate(GOLD, 6, alice, DAY)));
        currencies.setBaseRate(GOLD, 5, alice, DAY);
        assertEquals(5, fixture.app.queries().currencies().require(GOLD).baseRatePercent());
    }

    @Test
    void defaultCurrencyFollowsLawAndFallsBack() {
        PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
        currencies.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, null, DAY);
        PolityView town = fixture.app.queries().polity(nordhain).orElseThrow();
        assertEquals(EMERALD, currencies.defaultCurrencyOf(town));

        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Goldstandard",
                List.of(ParameterValue.of(StandardParameters.DEFAULT_CURRENCY, "GOLD")), DAY));
        town = fixture.app.queries().polity(nordhain).orElseThrow();
        assertEquals(GOLD, currencies.defaultCurrencyOf(town));
        assertEquals(GOLD, currencies.defaultCurrencyFor(alice, EMERALD));

        // Ein Gesetz auf eine nicht existierende Währung fällt auf die Gründungswährung zurück.
        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Phantasie",
                List.of(ParameterValue.of(StandardParameters.DEFAULT_CURRENCY, "NOPE")), DAY));
        town = fixture.app.queries().polity(nordhain).orElseThrow();
        assertEquals(EMERALD, currencies.defaultCurrencyOf(town));
    }

    @Test
    void emeraldBaseRateIsProtected() {
        assertEquals(ErrorCode.CURRENCY_PROTECTED, codeOf(() -> currencies.setBaseRate(EMERALD, 5, bank, DAY)));
    }

    @Test
    void takeAndRepayLoan() {
        currencies.create(GOLD, "Gold", "G", ExchangeRate.PARITY, bank, null, DAY);
        currencies.setBaseRate(GOLD, 10, bank, DAY);
        fund(bank, 100); // Smaragd, daher Kredit in Smaragd statt Gold
        credit.licenseBank(bank, DAY);
        // Smaragd hat Leitzins 0: Zins = nur Score-Aufschlag (Start 600 -> 500 bp).
        assertEquals(500, credit.quoteBasisPoints(bank, bob, EMERALD));

        Loan loan = credit.takeLoan(bank, bob, emeralds(10), 365, DAY);
        fund(bob, 1);
        assertEquals(emeralds(11), ledger.balanceOf(bob, EMERALD));
        assertEquals(emeralds(90), ledger.balanceOf(bank, EMERALD));
        assertEquals(1, loan.interest()); // 5 % von 10 Einheiten = 0,5, aufgerundet
                Money paid = credit.repay(loan.id(), bob, 1_000_000, DAY.plus(30));
        assertEquals(loan.totalDue(), paid.amount());
        assertEquals(LoanStatus.REPAID, fixture.app.queries().credit().loan(loan.id()).orElseThrow().status());
        assertEquals(CreditScore.START + CreditScore.REPAID_BONUS, fixture.app.queries().credit().scoreOf(bob));
    }

    @Test
    void nonBankAndInsufficientBankFundsAreRejected() {
        assertEquals(ErrorCode.NOT_A_BANK, codeOf(() -> credit.takeLoan(bank, bob, emeralds(1), 10, DAY)));
        credit.licenseBank(bank, DAY);
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, codeOf(() -> credit.takeLoan(bank, bob, emeralds(1), 10, DAY)));
        assertTrue(fixture.app.queries().credit().loans().isEmpty()); // nichts halb gebucht
    }

    @Test
    void lawCanForbidLendingLimitInterestAndRequireScore() {
        PolityId nordhain = fixture.found("Nordhain", bob, 0, 0);
        fund(bank, 100);
        credit.licenseBank(bank, DAY);

        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Zinsbremse",
                List.of(ParameterValue.of(StandardParameters.BANK_MAX_INTEREST_PERCENT, 2L)), DAY));
        assertEquals(ErrorCode.INTEREST_ABOVE_LIMIT, codeOf(() -> credit.takeLoan(bank, bob, emeralds(1), 10, DAY)));

        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Bonitaet",
                List.of(ParameterValue.of(StandardParameters.BANK_MAX_INTEREST_PERCENT, 50L),
                        ParameterValue.of(StandardParameters.BANK_MIN_CREDIT_SCORE, 700L)), DAY));
        assertEquals(ErrorCode.CREDIT_SCORE_TOO_LOW, codeOf(() -> credit.takeLoan(bank, bob, emeralds(1), 10, DAY)));

        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Kreditverbot",
                List.of(ParameterValue.of(StandardParameters.BANK_LENDING_ALLOWED, false)), DAY));
        assertEquals(ErrorCode.LENDING_NOT_ALLOWED, codeOf(() -> credit.takeLoan(bank, bob, emeralds(1), 10, DAY)));
    }

    @Test
    void unpaidLoanDefaultsAndRepayByStrangerIsRejected() {
        fund(bank, 100);
        credit.licenseBank(bank, DAY);
        Loan loan = credit.takeLoan(bank, bob, emeralds(5), 10, DAY);
        assertEquals(ErrorCode.NOT_LOAN_BORROWER, codeOf(() -> credit.repay(loan.id(), alice, 100, DAY)));
        assertTrue(credit.endOfDay(DAY.plus(10)).isEmpty());
        assertEquals(1, credit.endOfDay(DAY.plus(11)).size());
        assertEquals(CreditScore.START - CreditScore.DEFAULT_PENALTY, fixture.app.queries().credit().scoreOf(bob));
    }

    @Test
    void centralBankLendsToBanksAtBaseRateOnly() {
        PlayerRef chair = fixture.player();
        currencies.create(GOLD, "Gold", "G", ExchangeRate.PARITY, chair, null, DAY);
        currencies.setBaseRate(GOLD, 3, chair, DAY);
        credit.licenseBank(bank, DAY);
        var ledgerAggregate = fixture.repositories.ledger().load();
        ledgerAggregate.mint(chair, Money.of(10_000, GOLD), de.etiennebader.civ.domain.economy.SupplyChange.COMMODITY_DEPOSIT, DAY);
        fixture.repositories.ledger().save(ledgerAggregate);

        Loan loan = credit.takeLoan(chair, bank, Money.of(1_000, GOLD), 365, DAY);
        assertEquals(300, loan.rateBasisPoints()); // reiner Leitzins, kein Score-Aufschlag
        assertEquals(Money.of(1_000, GOLD), ledger.balanceOf(bank, GOLD));
        // Ein Nicht-Bank-Kreditnehmer bekäme von der Zentralbank nichts.
        assertEquals(ErrorCode.NOT_A_BANK, codeOf(() -> credit.takeLoan(chair, bob, Money.of(1, GOLD), 10, DAY)));
    }

    @Test
    void selfServiceIsRequiredForDirectLoans() {
        fund(bank, 100);
        credit.licenseBank(bank, DAY);
        assertEquals(ErrorCode.BANK_NOT_SELF_SERVICE,
                codeOf(() -> credit.takeSelfService(bank, bob, emeralds(5), 10, DAY)));
        credit.setSelfService(bank, true, DAY);
        credit.takeSelfService(bank, bob, emeralds(5), 10, DAY);
        credit.closeBank(bank, DAY);
        assertTrue(fixture.app.queries().credit().selfServiceBanks().isEmpty());
    }

    @Test
    void offerCanBeAcceptedDeclinedAndExpires() {
        fund(bank, 100);
        credit.licenseBank(bank, DAY);
        assertEquals(ErrorCode.NOT_A_BANK, codeOf(() -> credit.offerLoan(alice, bob, emeralds(5), 10, DAY)));
        var offer = credit.offerLoan(bank, bob, emeralds(5), 10, DAY);

        assertEquals(ErrorCode.NOT_OFFER_PARTY, codeOf(() -> credit.acceptOffer(offer.id(), alice, DAY)));
        Loan loan = credit.acceptOffer(offer.id(), bob, DAY.plus(1));
        assertEquals(emeralds(5), ledger.balanceOf(bob, EMERALD));
        assertEquals(bob, loan.borrower());
        assertTrue(fixture.app.queries().credit().offers().isEmpty());
        assertEquals(ErrorCode.UNKNOWN_LOAN_OFFER, codeOf(() -> credit.acceptOffer(offer.id(), bob, DAY.plus(2))));

        var declined = credit.offerLoan(bank, bob, emeralds(5), 10, DAY);
        credit.cancelOffer(declined.id(), bob, DAY);
        assertTrue(fixture.app.queries().credit().offers().isEmpty());

        var stale = credit.offerLoan(bank, bob, emeralds(5), 10, DAY);
        assertEquals(ErrorCode.UNKNOWN_LOAN_OFFER, codeOf(() -> credit.acceptOffer(stale.id(), bob, DAY.plus(8))));
        credit.endOfDay(DAY.plus(8));
        assertTrue(fixture.app.queries().credit().offers().isEmpty());
    }

    @Test
    void failedAcceptanceKeepsTheOffer() {
        credit.licenseBank(bank, DAY); // Bank ohne Guthaben
        var offer = credit.offerLoan(bank, bob, emeralds(5), 10, DAY);
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, codeOf(() -> credit.acceptOffer(offer.id(), bob, DAY)));
        assertEquals(1, fixture.app.queries().credit().offers().size());
        assertTrue(fixture.app.queries().credit().loans().isEmpty());
    }

    @Test
    void polityCanBorrowAndLendLikeAnyActor() {
        PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
        var town = fixture.app.queries().polity(nordhain).orElseThrow().asActor();
        fund(bank, 100);
        credit.licenseBank(bank, DAY);
        credit.setSelfService(bank, true, DAY);
        Loan loan = credit.takeSelfService(bank, town, emeralds(5), 10, DAY);
        assertEquals(emeralds(5), ledger.balanceOf(town, EMERALD));
        credit.repay(loan.id(), town, 1, DAY);
        credit.licenseBank(town, DAY); // auch Gemeinden/Firmen dürfen Bank sein
        assertTrue(fixture.app.queries().credit().isBank(town));
    }
}
