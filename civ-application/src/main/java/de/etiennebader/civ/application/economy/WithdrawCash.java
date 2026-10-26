package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Buchgeld wird als Bargeld (Banknoten und Münzen) ausgezahlt.
 */
public final class WithdrawCash {

    public record Command(ActorRef account, Money amount, Day day) {

        public Command {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public WithdrawCash(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Guthaben nicht reicht
     */
    public void execute(Command command) {
        Ledger ledger = aggregates.ledger();
        ledger.withdrawCash(command.account(), command.amount(), command.day());
        aggregates.commit(ledger);
    }
}
