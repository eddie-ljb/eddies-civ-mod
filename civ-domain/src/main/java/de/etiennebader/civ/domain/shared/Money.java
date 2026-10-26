package de.etiennebader.civ.domain.shared;

import java.util.Objects;

/**
 * Ein Geldbetrag in der kleinsten Einheit einer Währung.
 *
 * <p>Beträge sind ganzzahlig ({@code long}) und damit exakt. Rechenoperationen mit
 * unterschiedlichen Währungen werfen eine {@link CurrencyMismatchException},
 * Überläufe eine {@link ArithmeticException}.
 */
public record Money(long amount, CurrencyId currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money zero(CurrencyId currency) {
        return new Money(0, currency);
    }

    public static Money of(long amount, CurrencyId currency) {
        return new Money(amount, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(amount, factor), currency);
    }

    public boolean isNegative() {
        return amount < 0;
    }

    public boolean isZero() {
        return amount == 0;
    }

    public boolean isPositive() {
        return amount > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amount, other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount + " " + currency.code();
    }
}
