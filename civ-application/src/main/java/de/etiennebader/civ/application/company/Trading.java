package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.exchange.Order;
import de.etiennebader.civ.domain.exchange.Payments;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.OrderId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;

/**
 * Handel an der Börse: Orders aufgeben und streichen. Bezahlt wird sofort beim Geschäft vom Konto des Käufers an den
 * Verkäufer.
 */
public final class Trading {

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public Trading(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * Gibt eine Limit-Order auf.
     *
     * @param limit Einheiten je Aktie
     * @throws InsufficientFundsException wenn ein Käufer nicht einmal eine Aktie bezahlen kann
     */
    public StockExchange.Placement place(ActorRef trader, PolityId company, Side side, long quantity, long limit,
                                         Day day) {
        StockExchange exchange = aggregates.exchange();
        Ledger ledger = aggregates.ledger();
        CurrencyId currency = aggregates.polity(company).currency();
        if (side == Side.BUY) {
            Money available = ledger.balanceOf(trader, currency);
            if (available.amount() < limit) {
                throw new InsufficientFundsException(Money.of(limit, currency), available);
            }
        }
        Payments payments = new Payments() {
            @Override
            public boolean pay(ActorRef from, ActorRef to, long units) {
                try {
                    ledger.transfer(from, to, Money.of(units, currency), TransferReason.TRADE, day);
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            }

            @Override
            public long affordable(ActorRef payer, long price) {
                return ledger.balanceOf(payer, currency).amount() / price;
            }
        };
        StockExchange.Placement placement = exchange.place(OrderId.generate(ids), company, trader, side, quantity,
                limit, payments, day);
        aggregates.commit(ledger);
        aggregates.commit(exchange);
        return placement;
    }

    /** Streicht eine eigene offene Order. */
    public Order cancel(ActorRef trader, int number, Day day) {
        StockExchange exchange = aggregates.exchange();
        Order order = exchange.cancel(number, trader, day);
        aggregates.commit(exchange);
        return order;
    }

    /** Tagesende: verfallene Orders, Tageskurse. */
    public List<Order> endOfDay(Day day) {
        StockExchange exchange = aggregates.exchange();
        List<Order> expired = exchange.endOfDay(day);
        aggregates.commit(exchange);
        return expired;
    }
}
