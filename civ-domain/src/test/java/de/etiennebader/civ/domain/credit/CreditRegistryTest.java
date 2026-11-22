package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditRegistryTest {

    private static final Day DAY = Day.of(10);
    private static final CurrencyId EUR = CurrencyId.EMERALD;

    private final SeededIdGenerator ids = new SeededIdGenerator(9);
    private final PlayerRef bank = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final CreditRegistry registry = CreditRegistry.empty();

    private static ErrorCode codeOf(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }

    private Loan loan(long principal, int rateBp, long days) {
        return registry.issue(LoanId.generate(ids), bank, alice, EUR, principal, rateBp, days, DAY);
    }

    @Test
    void scoreMapsToRate() {
        assertEquals(0, CreditScore.premiumBasisPoints(CreditScore.MAX));
        assertEquals(1100, CreditScore.premiumBasisPoints(CreditScore.MIN));
        assertEquals(1100 + 500, CreditScore.rateBasisPoints(5, CreditScore.MIN));
        assertEquals(CreditScore.START, registry.scoreOf(alice));
    }

    @Test
    void interestIsSimpleAndRoundedUp() {
        assertEquals(100, Loan.interestFor(1_000, 1_000, 365)); // 10 % p. a. für ein Jahr
        assertEquals(1, Loan.interestFor(1, 1, 1)); // aufgerundet
        assertEquals(0, Loan.interestFor(1_000, 0, 30));
    }

    @Test
    void bankLicenseIsUnique() {
        registry.licenseBank(bank, DAY);
        assertTrue(registry.isBank(bank));
        assertEquals(ErrorCode.ALREADY_A_BANK, codeOf(() -> registry.licenseBank(bank, DAY)));
        registry.closeBank(bank, DAY);
        assertFalse(registry.isBank(bank));
        assertEquals(ErrorCode.NOT_A_BANK, codeOf(() -> registry.closeBank(bank, DAY)));
    }

    @Test
    void issueRejectsInvalidTerms() {
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(() -> loan(0, 100, 10)));
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(() -> loan(-5, 100, 10)));
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(() -> loan(100, 100, 0)));
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(() -> loan(100, -1, 10)));
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(() -> loan(Long.MAX_VALUE, 100_000, 3_650)));
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(
                () -> registry.issue(LoanId.generate(ids), bank, bank, EUR, 100, 100, 10, DAY)));
    }

    @Test
    void fullRepaymentRaisesScoreAndPartialDoesNot() {
        Loan loan = loan(1_000, 1_000, 365); // schuldet 1100
        assertEquals(1_100, loan.totalDue());
        assertEquals(500, registry.repay(loan.id(), alice, 500, DAY.plus(5)));
        assertEquals(CreditScore.START, registry.scoreOf(alice));
        assertEquals(600, registry.loan(loan.id()).orElseThrow().outstanding());
        assertEquals(600, registry.repay(loan.id(), alice, 9_999, DAY.plus(6))); // gekürzt
        assertEquals(LoanStatus.REPAID, registry.loan(loan.id()).orElseThrow().status());
        assertEquals(CreditScore.START + CreditScore.REPAID_BONUS, registry.scoreOf(alice));
        assertEquals(ErrorCode.LOAN_NOT_ACTIVE, codeOf(() -> registry.repay(loan.id(), alice, 1, DAY)));
    }

    @Test
    void onlyBorrowerMayRepay() {
        Loan loan = loan(100, 0, 10);
        assertEquals(ErrorCode.NOT_LOAN_BORROWER, codeOf(() -> registry.repay(loan.id(), bank, 10, DAY)));
        assertEquals(ErrorCode.INVALID_LOAN_TERMS, codeOf(() -> registry.repay(loan.id(), alice, 0, DAY)));
    }

    @Test
    void overdueLoanDefaultsOnceAndLowersScore() {
        Loan loan = loan(100, 0, 10);
        assertTrue(registry.markOverdue(DAY.plus(10)).isEmpty()); // am Fälligkeitstag noch nicht
        assertEquals(1, registry.markOverdue(DAY.plus(11)).size());
        assertEquals(CreditScore.START - CreditScore.DEFAULT_PENALTY, registry.scoreOf(alice));
        assertTrue(registry.markOverdue(DAY.plus(12)).isEmpty()); // nicht doppelt bestrafen
        // Spätes Begleichen löscht die Schuld, gibt aber keinen Bonus zurück.
        registry.repay(loan.id(), alice, 100, DAY.plus(13));
        assertEquals(LoanStatus.REPAID, registry.loan(loan.id()).orElseThrow().status());
        assertEquals(CreditScore.START - CreditScore.DEFAULT_PENALTY, registry.scoreOf(alice));
    }

    @Test
    void scoreStaysInBounds() {
        for (int i = 0; i < 10; i++) {
            loan(10, 0, 1);
        }
        registry.markOverdue(DAY.plus(5));
        assertEquals(CreditScore.MIN, registry.scoreOf(alice));
    }

    @Test
    void restoreKeepsEverything() {
        registry.licenseBank(bank, DAY);
        Loan loan = loan(100, 100, 30);
        CreditRegistry restored = CreditRegistry.restore(registry.banks(), registry.scores(), registry.loans());
        assertTrue(restored.isBank(bank));
        assertEquals(loan, restored.loan(loan.id()).orElseThrow());
    }
}
