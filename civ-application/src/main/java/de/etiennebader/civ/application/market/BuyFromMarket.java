package de.etiennebader.civ.application.market;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.Market;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;

/**
 * Ein Akteur kauft Waren aus dem Lager des Servermarkts; der Kaufpreis wird von seinem Konto
 * abgebucht und aus dem Umlauf genommen. Die Waren übergibt der Aufrufer danach im Spiel.
 */
public final class BuyFromMarket {

    public record Command(ActorRef buyer, Commodity commodity, int quantity, long basePrice, PriceLevel level,
                          Day day) {

        public Command {
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(commodity, "commodity");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public BuyFromMarket(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return der Kaufpreis
     * @throws de.etiennebader.civ.domain.economy.InsufficientFundsException wenn das Konto nicht reicht
     */
    public Money execute(Command command) {
        Market market = aggregates.market();
        Money cost = market.purchaseCost(command.commodity(), command.quantity(), command.basePrice(), command.level(),
                command.day());
        Ledger ledger = aggregates.ledger();
        ledger.burn(command.buyer(), cost, SupplyChange.MARKET_PURCHASE, command.day());
        market.buy(command.buyer(), command.commodity(), command.quantity(), command.basePrice(), command.level(),
                command.day());
        aggregates.commit(market);
        aggregates.commit(ledger);
        return cost;
    }
}
