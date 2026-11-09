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
 * Ein Akteur verkauft Waren an den Servermarkt; der Erlös wird ihm neu gutgeschrieben.
 * Die Waren selbst entfernt der Aufrufer aus dem Spiel, bevor er diesen Use Case aufruft.
 */
public final class SellToMarket {

    /**
     * @param basePrice Grundpreis eines Stücks in kleinster Währungseinheit
     */
    public record Command(ActorRef seller, Commodity commodity, int quantity, long basePrice, PriceLevel level,
                          Day day) {

        public Command {
            Objects.requireNonNull(seller, "seller");
            Objects.requireNonNull(commodity, "commodity");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public SellToMarket(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return der Erlös
     */
    public Money execute(Command command) {
        Market market = aggregates.market();
        Money proceeds = market.sell(command.seller(), command.commodity(), command.quantity(), command.basePrice(),
                command.level(), command.day());
        Ledger ledger = aggregates.ledger();
        ledger.mint(command.seller(), proceeds, SupplyChange.MARKET_SALE, command.day());
        aggregates.commit(market);
        aggregates.commit(ledger);
        return proceeds;
    }
}
