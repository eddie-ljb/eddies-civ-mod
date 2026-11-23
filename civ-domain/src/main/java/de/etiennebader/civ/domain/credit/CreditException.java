package de.etiennebader.civ.domain.credit;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/** Fachlicher Fehler im Kreditwesen; der {@link ErrorCode} sagt, welcher. */
public final class CreditException extends DomainException {

    private CreditException(ErrorCode code, Object... arguments) {
        super(code, arguments);
    }

    public static CreditException notABank(ActorRef actor) {
        return new CreditException(ErrorCode.NOT_A_BANK, actor);
    }

    public static CreditException alreadyABank(ActorRef actor) {
        return new CreditException(ErrorCode.ALREADY_A_BANK, actor);
    }

    public static CreditException invalidTerms() {
        return new CreditException(ErrorCode.INVALID_LOAN_TERMS);
    }

    public static CreditException unknownLoan(LoanId id) {
        return new CreditException(ErrorCode.UNKNOWN_LOAN, id.value());
    }

    public static CreditException notActive(LoanId id) {
        return new CreditException(ErrorCode.LOAN_NOT_ACTIVE, id.value());
    }

    public static CreditException notBorrower(LoanId id, ActorRef actor) {
        return new CreditException(ErrorCode.NOT_LOAN_BORROWER, id.value(), actor);
    }

    public static CreditException interestAboveLimit(long percent, long limit) {
        return new CreditException(ErrorCode.INTEREST_ABOVE_LIMIT, percent, limit);
    }

    public static CreditException scoreTooLow(int score, long minimum) {
        return new CreditException(ErrorCode.CREDIT_SCORE_TOO_LOW, score, minimum);
    }

    public static CreditException lendingNotAllowed() {
        return new CreditException(ErrorCode.LENDING_NOT_ALLOWED);
    }

    public static CreditException unknownOffer(LoanId id) {
        return new CreditException(ErrorCode.UNKNOWN_LOAN_OFFER, id.value());
    }

    public static CreditException notOfferRecipient(LoanId id, ActorRef actor) {
        return new CreditException(ErrorCode.NOT_OFFER_PARTY, id.value(), actor);
    }

    public static CreditException notSelfService(ActorRef bank) {
        return new CreditException(ErrorCode.BANK_NOT_SELF_SERVICE, bank);
    }
}
