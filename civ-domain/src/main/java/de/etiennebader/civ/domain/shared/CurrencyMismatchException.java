package de.etiennebader.civ.domain.shared;

/**
 * Es wurde mit Beträgen zweier verschiedener Währungen gerechnet.
 */
public final class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(CurrencyId first, CurrencyId second) {
        super(ErrorCode.CURRENCY_MISMATCH, first, second);
    }
}
