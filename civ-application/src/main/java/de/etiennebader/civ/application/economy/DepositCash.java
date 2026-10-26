package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Bargeld wird auf ein Konto eingezahlt. Wer es einzahlt, muss es nicht selbst abgehoben haben.
 */
public final class DepositCash {

    public record Command(ActorRef account, Money amount, Day day) {

        public Command {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public DepositCash(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws de.etiennebader.civ.domain.economy.CashIntegrityException wenn mehr eingezahlt wird, als im Umlauf ist
     */
    public void execute(Command command) {
        Ledger ledger = aggregates.ledger();
        ledger.depositCash(command.account(), command.amount(), command.day());
        aggregates.commit(ledger);
    }
}
