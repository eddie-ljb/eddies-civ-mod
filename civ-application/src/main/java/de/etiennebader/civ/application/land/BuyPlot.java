package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotNotForSaleException;
import de.etiennebader.civ.domain.land.UnknownPlotException;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Kauft ein angebotenes Grundstück zum geforderten Preis. Eigentum und Geld wechseln gemeinsam oder gar nicht.
 */
public final class BuyPlot {

    public record Command(PolityId polity, PlotId plot, ActorRef buyer, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(buyer, "buyer");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public BuyPlot(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return der gezahlte Preis
     * @throws PlotNotForSaleException     wenn das Grundstück nicht angeboten wird
     * @throws InsufficientFundsException wenn der Käufer den Preis nicht bezahlen kann
     */
    public Money execute(Command command) {
        aggregates.polity(command.polity());
        LandRegistry land = aggregates.land(command.polity());
        Plot plot = land.plot(command.plot()).orElseThrow(() -> new UnknownPlotException(command.plot()));
        Money price = plot.askingPrice().orElseThrow(() -> new PlotNotForSaleException(command.plot()));
        ActorRef seller = plot.holder();
        Ledger ledger = aggregates.ledger();
        Money available = ledger.balanceOf(command.buyer(), price.currency());
        if (available.compareTo(price) < 0) {
            throw new InsufficientFundsException(price, available);
        }
        land.transfer(command.plot(), seller, command.buyer(), command.day());
        if (price.isPositive()) {
            ledger.transfer(command.buyer(), seller, price, TransferReason.TRADE, command.day());
        }
        aggregates.commit(land);
        aggregates.commit(ledger);
        return price;
    }
}
