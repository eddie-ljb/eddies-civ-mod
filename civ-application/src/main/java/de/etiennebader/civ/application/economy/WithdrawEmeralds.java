package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Buchgeld wird in Smaragde zurückgetauscht. Senkt die Geldmenge.
 */
public final class WithdrawEmeralds {

    public record Command(ActorRef account, Money amount, Day day) {

        public Command {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public WithdrawEmeralds(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Guthaben nicht reicht
     */
    public void execute(Command command) {
        Ledger ledger = aggregates.ledger();
        ledger.burn(command.account(), command.amount(), SupplyChange.COMMODITY_WITHDRAWAL, command.day());
        aggregates.commit(ledger);
    }
}
