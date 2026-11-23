package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Kreditangebot eines Kreditgebers an einen Kreditnehmer. Zins und Auszahlung ergeben sich erst bei der Annahme
 * aus dem dann geltenden Leitzins und Kreditscore; bis {@code expiresOn} (einschließlich) lässt es sich annehmen.
 */
public record LoanOffer(LoanId id, ActorRef lender, ActorRef borrower, CurrencyId currency, long principal,
                        long termDays, Day offeredOn, Day expiresOn) {

    /** So viele Tage bleibt ein Angebot offen. */
    public static final long VALID_DAYS = 7;

    public LoanOffer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lender, "lender");
        Objects.requireNonNull(borrower, "borrower");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(offeredOn, "offeredOn");
        Objects.requireNonNull(expiresOn, "expiresOn");
        if (principal <= 0 || termDays < 1 || termDays > CreditRegistry.MAX_TERM_DAYS || lender.equals(borrower)) {
            throw new IllegalArgumentException("Ungültiges Kreditangebot");
        }
    }
}
