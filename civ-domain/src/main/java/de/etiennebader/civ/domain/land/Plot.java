package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

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

    Plot(PlotId id, Area area, PlotType type, ActorRef holder, Day holderSince) {
        this.id = Objects.requireNonNull(id, "id");
        this.area = Objects.requireNonNull(area, "area");
        this.type = Objects.requireNonNull(type, "type");
        this.holder = Objects.requireNonNull(holder, "holder");
        this.holderSince = Objects.requireNonNull(holderSince, "holderSince");
    }

    void changeHolder(ActorRef newHolder, Day day) {
        this.holder = Objects.requireNonNull(newHolder, "newHolder");
        this.holderSince = Objects.requireNonNull(day, "day");
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
