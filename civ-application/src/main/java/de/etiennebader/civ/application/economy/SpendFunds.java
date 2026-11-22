package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/** Ein Konto gibt Geld aus, das den Umlauf verlässt (z. B. Ausrüstung und Ausbildung einer Garnison). */
public final class SpendFunds {

    public record Command(ActorRef from, Money amount, SupplyChange reason, Day day) {

        public Command {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public SpendFunds(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /** @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Guthaben nicht reicht */
    public void execute(Command command) {
        Ledger ledger = aggregates.ledger();
        ledger.burn(command.from(), command.amount(), command.reason(), command.day());
        aggregates.commit(ledger);
    }
}
