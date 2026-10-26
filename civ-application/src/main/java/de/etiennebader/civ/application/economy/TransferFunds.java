package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Überweist Buchgeld von einem Konto auf ein anderes.
 */
public final class TransferFunds {

    public record Command(ActorRef from, ActorRef to, Money amount, TransferReason reason, Day day) {

        public Command {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public TransferFunds(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Guthaben nicht reicht
     */
    public void execute(Command command) {
        Ledger ledger = aggregates.ledger();
        ledger.transfer(command.from(), command.to(), command.amount(), command.reason(), command.day());
        aggregates.commit(ledger);
    }
}
