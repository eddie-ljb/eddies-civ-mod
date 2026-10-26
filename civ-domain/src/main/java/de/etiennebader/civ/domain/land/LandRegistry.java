package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregat Grundbuch einer Polity.
 *
 * <p>Invarianten: Grundstücke liegen vollständig im Gebiet der Polity und überlappen nicht.
 * Botschaften gehören immer einer fremden Polity, sobald sie vergeben sind.
 */
public final class LandRegistry extends AggregateRoot<LandEvent> implements LandView {

    private final PolityId owner;
    private final Map<PlotId, Plot> plots = new LinkedHashMap<>();

    private LandRegistry(PolityId owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /** Legt ein leeres Grundbuch für eine Polity an. */
    public static LandRegistry open(PolityId owner) {
        return new LandRegistry(owner);
    }

    /**
     * Legt ein neues Grundstück an. Es gehört zunächst der Polity selbst (Gemeindeland).
     *
     * @throws PlotOutsideTerritoryException wenn ein Teil außerhalb des eigenen Gebiets liegt
     * @throws PlotOverlapException wenn es ein bestehendes Grundstück überlappt
     */
    public void createPlot(PlotId id, Area area, PlotType type, TerritoryView territory, Day day) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(day, "day");
        if (plots.containsKey(id)) {
            throw new IllegalArgumentException("Grundstück existiert bereits: " + id);
        }
        requireWithinTerritory(area, territory);
        requireNoOverlap(area);
        plots.put(id, new Plot(id, area, type, ownerAsActor(), day));
        raise(new PlotCreated(owner, id, area, type, day));
    }

    /**
     * Vergibt ein Grundstück der Polity an einen Akteur.
     *
     * @throws UnknownPlotException wenn es das Grundstück nicht gibt
     * @throws NotPlotHolderException wenn das Grundstück nicht mehr der Polity gehört
     * @throws InvalidEmbassyHolderException wenn eine Botschaft nicht an eine fremde Polity geht
     */
    public void assign(PlotId plotId, ActorRef holder, Day day) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(day, "day");
        Plot plot = require(plotId);
        PolityRef polity = ownerAsActor();
        if (!plot.isHeldBy(polity)) {
            throw new NotPlotHolderException(polity, plotId);
        }
        requireValidHolder(plot, holder);
        plot.changeHolder(holder, day);
        raise(new PlotAssigned(owner, plotId, holder, day));
    }

    /**
     * Wechselt den Inhaber eines Grundstücks, z. B. bei einem Verkauf.
     *
     * @throws UnknownPlotException wenn es das Grundstück nicht gibt
     * @throws NotPlotHolderException wenn {@code from} nicht Inhaber ist
     * @throws InvalidEmbassyHolderException wenn eine Botschaft nicht an eine fremde Polity geht
     */
    public void transfer(PlotId plotId, ActorRef from, ActorRef to, Day day) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(day, "day");
        Plot plot = require(plotId);
        if (!plot.isHeldBy(from)) {
            throw new NotPlotHolderException(from, plotId);
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("Absender und Empfänger sind identisch: " + from);
        }
        requireValidHolder(plot, to);
        plot.changeHolder(to, day);
        raise(new PlotTransferred(owner, plotId, from, to, day));
    }

    /**
     * Löst ein Grundstück auf. Seine Fläche wird wieder Gemeindeland.
     *
     * @throws UnknownPlotException wenn es das Grundstück nicht gibt
     */
    public void dissolve(PlotId plotId, Day day) {
        Objects.requireNonNull(day, "day");
        Plot plot = require(plotId);
        plots.remove(plotId);
        raise(new PlotDissolved(owner, plotId, plot.holder(), day));
    }

    @Override
    public PolityId owner() {
        return owner;
    }

    @Override
    public Optional<Plot> plotAt(DimensionId dimension, int x, int z) {
        Objects.requireNonNull(dimension, "dimension");
        return plots.values().stream()
                .filter(plot -> plot.area().contains(dimension, x, z))
                .findFirst();
    }

    @Override
    public Collection<Plot> plots() {
        return List.copyOf(plots.values());
    }

    @Override
    public Collection<Plot> plotsHeldBy(ActorRef holder) {
        Objects.requireNonNull(holder, "holder");
        return plots.values().stream()
                .filter(plot -> plot.isHeldBy(holder))
                .toList();
    }

    @Override
    public boolean hasPlotsIn(ChunkCoord chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return plots.values().stream()
                .anyMatch(plot -> plot.area().touches(chunk));
    }

    public Optional<Plot> plot(PlotId plotId) {
        return Optional.ofNullable(plots.get(Objects.requireNonNull(plotId, "plotId")));
    }

    private Plot require(PlotId plotId) {
        return plot(plotId).orElseThrow(() -> new UnknownPlotException(plotId));
    }

    private PolityRef ownerAsActor() {
        return new PolityRef(owner);
    }

    private void requireWithinTerritory(Area area, TerritoryView territory) {
        area.chunks().stream()
                .filter(chunk -> territory.sovereignOf(chunk).filter(owner::equals).isEmpty())
                .findFirst()
                .ifPresent(chunk -> {
                    throw new PlotOutsideTerritoryException(chunk);
                });
    }

    private void requireNoOverlap(Area area) {
        plots.values().stream()
                .filter(existing -> existing.area().overlaps(area))
                .findFirst()
                .ifPresent(existing -> {
                    throw new PlotOverlapException(existing.id());
                });
    }

    private void requireValidHolder(Plot plot, ActorRef holder) {
        boolean isEmbassy = plot.type() == PlotType.EMBASSY;
        boolean isForeignPolity = holder instanceof PolityRef polity && !polity.id().equals(owner);
        if (isEmbassy && !isForeignPolity) {
            throw new InvalidEmbassyHolderException(holder);
        }
    }
}
