package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;

/**
 * Das Guthaben reicht nicht. Argumente: fehlender, verfügbarer und benötigter Betrag.
 */
public final class InsufficientFundsException extends DomainException {

    private final Money required;
    private final Money available;

    public InsufficientFundsException(Money required, Money available) {
        super(ErrorCode.INSUFFICIENT_FUNDS, required.minus(available), available, required);
        this.required = required;
        this.available = available;
    }

    public Money required() {
        return required;
    }

    public Money available() {
        return available;
    }

    public Money missing() {
        return required.minus(available);
    }
}
