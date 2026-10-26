package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Smaragde werden am Bank- oder Rathausblock eingezahlt und als Buchgeld gutgeschrieben. Erhöht die Geldmenge.
 */
public final class DepositEmeralds {

    public record Command(ActorRef account, Money amount, Day day) {

        public Command {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public DepositEmeralds(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Ledger ledger = aggregates.ledger();
        ledger.mint(command.account(), command.amount(), SupplyChange.COMMODITY_DEPOSIT, command.day());
        aggregates.commit(ledger);
    }
}
