package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Verkauft ein Grundstück gegen Buchgeld: Eigentum und Kaufpreis wechseln gemeinsam oder gar nicht.
 * Verkäufer kann die Gemeinde selbst oder ein Inhaber sein, Käufer jeder Akteur.
 *
 * <p>Ablauf: zuerst die Deckung des Käufers prüfen, dann das Grundstück übertragen (prüft Inhaber
 * und Botschaftsregel), zuletzt den Kaufpreis buchen, der dann nicht mehr scheitern kann.
 */
public final class SellPlot {

    public record Command(PolityId polity, PlotId plot, ActorRef seller, ActorRef buyer, Money price, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(seller, "seller");
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(day, "day");
            if (price.isNegative()) {
                throw new IllegalArgumentException("Kaufpreis darf nicht negativ sein: " + price);
            }
        }
    }

    private final Aggregates aggregates;

    public SellPlot(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws InsufficientFundsException wenn der Käufer den Preis nicht bezahlen kann; dann ändert sich nichts
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        if (!command.price().currency().equals(polity.currency())) {
            throw new IllegalArgumentException("Kaufpreis muss in der Währung der Gemeinde sein: " + command.price());
        }
        LandRegistry land = aggregates.land(polity.id());
        Ledger ledger = aggregates.ledger();
        Money available = ledger.balanceOf(command.buyer(), command.price().currency());
        if (available.compareTo(command.price()) < 0) {
            throw new InsufficientFundsException(command.price(), available);
        }

        land.transfer(command.plot(), command.seller(), command.buyer(), command.day());
        if (command.price().isPositive()) {
            ledger.transfer(command.buyer(), command.seller(), command.price(), TransferReason.TRADE, command.day());
        }
        aggregates.commit(land);
        aggregates.commit(ledger);
    }
}
