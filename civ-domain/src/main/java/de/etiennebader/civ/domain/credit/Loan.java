package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Kreditvertrag. Die Zinsen werden bei Vergabe fest vereinbart (einfacher Zins auf die Laufzeit);
 * {@link #totalDue()} ist Kapital plus Zinsen.
 *
 * @param rateBasisPoints vereinbarter Jahreszins in Basispunkten
 * @param repaid          bisher zurückgezahlt (zuerst Zinsen und Kapital zusammen gerechnet)
 */
public record Loan(LoanId id, ActorRef lender, ActorRef borrower, CurrencyId currency, long principal,
                   int rateBasisPoints, long interest, Day issuedOn, Day dueOn, long repaid, LoanStatus status) {

    public Loan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lender, "lender");
        Objects.requireNonNull(borrower, "borrower");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(issuedOn, "issuedOn");
        Objects.requireNonNull(dueOn, "dueOn");
        Objects.requireNonNull(status, "status");
        if (principal <= 0 || interest < 0 || repaid < 0 || rateBasisPoints < 0) {
            throw new IllegalArgumentException("Ungültige Kreditbeträge");
        }
        if (dueOn.isBefore(issuedOn)) {
            throw new IllegalArgumentException("Fälligkeit liegt vor der Vergabe");
        }
    }

    /** Zinsen als einfacher Jahreszins über die Laufzeit, aufgerundet. */
    public static long interestFor(long principal, int rateBasisPoints, long termDays) {
        java.math.BigInteger numerator = java.math.BigInteger.valueOf(principal)
                .multiply(java.math.BigInteger.valueOf(rateBasisPoints))
                .multiply(java.math.BigInteger.valueOf(termDays));
        java.math.BigInteger denominator = java.math.BigInteger.valueOf(10_000L * 365L);
        long result = numerator.add(denominator).subtract(java.math.BigInteger.ONE).divide(denominator).longValueExact();
        return result;
    }

    public long totalDue() {
        return Math.addExact(principal, interest);
    }

    public long outstanding() {
        return Math.max(0, totalDue() - repaid);
    }

    public boolean isActive() {
        return status == LoanStatus.ACTIVE;
    }

    Loan withRepaid(long newRepaid, LoanStatus newStatus) {
        return new Loan(id, lender, borrower, currency, principal, rateBasisPoints, interest, issuedOn, dueOn,
                newRepaid, newStatus);
    }
}
