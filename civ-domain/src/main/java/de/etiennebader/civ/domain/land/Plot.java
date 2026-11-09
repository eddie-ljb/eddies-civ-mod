package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Grundstück. Entity innerhalb des {@link LandRegistry}-Aggregats; der Inhaber ändert sich
 * nur über das Grundbuch.
 */
public final class Plot {

    private final PlotId id;
    private final Area area;
    private final PlotType type;
    private ActorRef holder;
    private Day holderSince;
    private Optional<Money> askingPrice;

    Plot(PlotId id, Area area, PlotType type, ActorRef holder, Day holderSince, Optional<Money> askingPrice) {
        this.id = Objects.requireNonNull(id, "id");
        this.area = Objects.requireNonNull(area, "area");
        this.type = Objects.requireNonNull(type, "type");
        this.holder = Objects.requireNonNull(holder, "holder");
        this.holderSince = Objects.requireNonNull(holderSince, "holderSince");
        this.askingPrice = Objects.requireNonNull(askingPrice, "askingPrice");
    }

    /** Neuer Inhaber; ein laufendes Verkaufsangebot erlischt. */
    void changeHolder(ActorRef newHolder, Day day) {
        this.holder = Objects.requireNonNull(newHolder, "newHolder");
        this.holderSince = Objects.requireNonNull(day, "day");
        this.askingPrice = Optional.empty();
    }

    void offer(Optional<Money> price) {
        this.askingPrice = Objects.requireNonNull(price, "price");
    }

    public PlotId id() {
        return id;
    }

    public Area area() {
        return area;
    }

    public PlotType type() {
        return type;
    }

    public ActorRef holder() {
        return holder;
    }

    /** Seit wann der aktuelle Inhaber das Grundstück hält. */
    public Day holderSince() {
        return holderSince;
    }

    /** Der Preis, zu dem der Inhaber das Grundstück anbietet; leer, wenn es nicht zum Verkauf steht. */
    public Optional<Money> askingPrice() {
        return askingPrice;
    }

    public boolean isForSale() {
        return askingPrice.isPresent();
    }

    public PlotSnapshot snapshot() {
        return new PlotSnapshot(id, area, type, holder, holderSince, askingPrice);
    }

    public boolean isHeldBy(ActorRef actor) {
        return holder.equals(actor);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Plot plot && id.equals(plot.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Plot[" + id.value() + ", " + type + ", " + area + ", holder=" + holder + "]";
    }
}
