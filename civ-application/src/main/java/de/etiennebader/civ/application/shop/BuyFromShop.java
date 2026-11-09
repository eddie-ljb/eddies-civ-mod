package de.etiennebader.civ.application.shop;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shop.Shop;
import de.etiennebader.civ.domain.shop.ShopRegistry;
import de.etiennebader.civ.domain.shop.UnknownShopException;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Spieler kauft in einem Laden. Er zahlt Stückpreis × Menge; liegt der Laden im Gebiet einer Gemeinde,
 * geht deren Umsatzsteuer ({@link StandardParameters#SALES_TAX_PERCENT}) an die Gemeindekasse, der Rest an den Inhaber.
 * Die Ware bewegt der Aufrufer, nachdem das Geld geflossen ist.
 */
public final class BuyFromShop {

    public record Command(BlockPosition position, ActorRef buyer, int quantity, Day day) {

        public Command {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(day, "day");
            if (quantity < 1) {
                throw new IllegalArgumentException("Menge muss mindestens 1 sein: " + quantity);
            }
        }
    }

    /** Was der Kauf gekostet hat. */
    public record Receipt(Shop shop, Money total, Money tax) {
    }

    private final Aggregates aggregates;

    public BuyFromShop(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws UnknownShopException       wenn dort kein Laden ist
     * @throws InsufficientFundsException wenn der Käufer nicht genug Geld hat
     */
    public Receipt execute(Command command) {
        ShopRegistry shops = aggregates.shops();
        Shop shop = shops.shopAt(command.position()).orElseThrow(() -> new UnknownShopException(command.position()));
        if (shop.owner().equals(command.buyer())) {
            throw new IllegalArgumentException("Inhaber kann nicht im eigenen Laden kaufen");
        }
        Money total = Money.of(Math.multiplyExact(shop.price().amount(), (long) command.quantity()), shop.price().currency());
        Ledger ledger = aggregates.ledger();
        Money available = ledger.balanceOf(command.buyer(), total.currency());
        if (available.compareTo(total) < 0) {
            throw new InsufficientFundsException(total, available);
        }
        Optional<Polity> town = aggregates.territory().sovereignOf(command.position().chunk())
                .flatMap(id -> aggregates.repositories().polities().findById(id))
                .filter(polity -> polity.currency().equals(total.currency()));
        long percent = town.map(polity -> polity.laws().valueOf(StandardParameters.SALES_TAX_PERCENT)).orElse(0L);
        Money tax = Money.of(total.amount() * percent / 100, total.currency());
        Money toOwner = Money.of(total.amount() - tax.amount(), total.currency());
        if (toOwner.isPositive()) {
            ledger.transfer(command.buyer(), shop.owner(), toOwner, TransferReason.TRADE, command.day());
        }
        if (tax.isPositive()) {
            ledger.transfer(command.buyer(), town.orElseThrow().asActor(), tax, TransferReason.SALES_TAX, command.day());
        }
        shops.recordSale(command.position(), command.buyer(), command.quantity(), total, tax, command.day());
        aggregates.commit(ledger);
        aggregates.commit(shops);
        return new Receipt(shop, total, tax);
    }
}
