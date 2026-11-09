package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Der Inhaber bietet ein Grundstück zum Verkauf an ({@code price} gesetzt) oder zieht das Angebot zurück
 * ({@code price} leer). Inhaber kann auch die Gemeinde selbst sein.
 */
public final class OfferPlot {

    public record Command(PolityId polity, PlotId plot, ActorRef holder, Optional<Money> price, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(plot, "plot");
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public OfferPlot(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        LandRegistry land = aggregates.land(polity.id());
        if (command.price().isPresent()) {
            Money price = command.price().get();
            if (!price.currency().equals(polity.currency())) {
                throw new IllegalArgumentException("Preis muss in der Währung der Gemeinde sein: " + price);
            }
            land.offerForSale(command.plot(), command.holder(), price, command.day());
        } else {
            land.withdrawOffer(command.plot(), command.holder(), command.day());
        }
        aggregates.commit(land);
    }
}
