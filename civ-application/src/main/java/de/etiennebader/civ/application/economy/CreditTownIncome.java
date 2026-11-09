package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Schreibt der Gemeindekasse die tägliche Wirtschaftsleistung ihrer Dorfbewohner gut. Das Geld entsteht neu.
 */
public final class CreditTownIncome {

    public record Command(PolityId polity, Money amount, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(day, "day");
            if (!amount.isPositive()) {
                throw new IllegalArgumentException("Einnahmen müssen positiv sein: " + amount);
            }
        }
    }

    private final Aggregates aggregates;

    public CreditTownIncome(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        if (!command.amount().currency().equals(polity.currency())) {
            throw new IllegalArgumentException("Einnahmen müssen in der Währung der Gemeinde sein: " + command.amount());
        }
        Ledger ledger = aggregates.ledger();
        ledger.mint(polity.asActor(), command.amount(), SupplyChange.VILLAGER_ECONOMY, command.day());
        aggregates.commit(ledger);
    }
}
