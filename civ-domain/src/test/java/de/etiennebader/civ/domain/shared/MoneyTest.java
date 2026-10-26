package de.etiennebader.civ.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    private static final CurrencyId EMERALD = CurrencyId.EMERALD;
    private static final CurrencyId TALER = new CurrencyId("TALER");

    @Test
    @DisplayName("Addition und Subtraktion gleicher Währung")
    void addsAndSubtractsSameCurrency() {
        Money ten = Money.of(10, EMERALD);
        Money three = Money.of(3, EMERALD);

        assertEquals(Money.of(13, EMERALD), ten.plus(three));
        assertEquals(Money.of(7, EMERALD), ten.minus(three));
        assertEquals(Money.of(-7, EMERALD), three.minus(ten));
        assertEquals(Money.of(30, EMERALD), ten.times(3));
    }

    @Test
    @DisplayName("Addition verschiedener Währungen → CURRENCY_MISMATCH")
    void rejectsMixedCurrencies() {
        assertDomainError(ErrorCode.CURRENCY_MISMATCH, () -> Money.of(1, EMERALD).plus(Money.of(1, TALER)));
        assertDomainError(ErrorCode.CURRENCY_MISMATCH, () -> Money.of(1, EMERALD).compareTo(Money.of(1, TALER)));
    }

    @Test
    @DisplayName("Vorzeichen-Abfragen")
    void reportsSign() {
        assertTrue(Money.zero(EMERALD).isZero());
        assertTrue(Money.of(-1, EMERALD).isNegative());
        assertTrue(Money.of(1, EMERALD).isPositive());
    }

    @Test
    @DisplayName("Überlauf wird erkannt statt still falsch zu rechnen")
    void detectsOverflow() {
        Money max = Money.of(Long.MAX_VALUE, EMERALD);

        assertThrows(ArithmeticException.class, () -> max.plus(Money.of(1, EMERALD)));
        assertThrows(ArithmeticException.class, () -> max.times(2));
    }

    @Test
    @DisplayName("Ungültiger Währungscode → INVALID_CURRENCY_CODE")
    void rejectsInvalidCurrencyCode() {
        assertDomainError(ErrorCode.INVALID_CURRENCY_CODE, () -> new CurrencyId("taler"));
        assertDomainError(ErrorCode.INVALID_CURRENCY_CODE, () -> new CurrencyId("X"));
    }
}
