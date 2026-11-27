package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Street;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hausnummern und Wohnsitze.
 *
 * <p>Ein Grundstück liegt an der nächsten Straße, wenn sie höchstens {@value #REACH} Blöcke entfernt ist. Die Nummern
 * laufen entlang der Straße: auf der Nord- bzw. Westseite ungerade (1, 3, 5 …), gegenüber gerade (2, 4, 6 …).
 *
 * <p>Wohnsitz eines Spielers ist sein erstes Wohngrundstück, sonst ein gemietetes, sonst sein erstes Grundstück
 * überhaupt. Ein Dorfbewohner wohnt dort, wo er zuletzt gesehen wurde, wenn das ein Grundstück ist.
 */
public final class Addresses {

    /** So weit darf ein Grundstück von seiner Straße entfernt sein. */
    public static final int REACH = 12;

    private final WorldQueries queries;

    public Addresses(WorldQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /** Die Anschrift eines Grundstücks; leer, wenn keine Straße nah genug ist. */
    public Optional<Address> of(PolityView town, Plot plot) {
        LandView land = queries.land(town.id());
        Optional<Street> street = nearest(town, plot.area());
        if (street.isEmpty()) {
            return Optional.empty();
        }
        List<Plot> neighbours = land.plots().stream()
                .filter(other -> nearest(town, other.area()).filter(street.get()::equals).isPresent())
                .toList();
        boolean alongX = street.get().runsAlongX();
        double streetCenter = alongX ? center(street.get().area(), false) : center(street.get().area(), true);
        boolean firstSide = side(plot.area(), alongX) < streetCenter;
        List<Plot> sameSide = new ArrayList<>(neighbours.stream()
                .filter(other -> (side(other.area(), alongX) < streetCenter) == firstSide)
                .toList());
        sameSide.sort(Comparator.comparingDouble(other -> along(other.area(), alongX)));
        int index = 0;
        while (index < sameSide.size() && !sameSide.get(index).id().equals(plot.id())) {
            index++;
        }
        int number = firstSide ? 2 * index + 1 : 2 * index + 2;
        return Optional.of(new Address(town.id(), town.name().value(), town.postalCode(), street.get().name(), number,
                plot.id()));
    }

    /** Alle Anschriften einer Gemeinde in Straßen- und Nummernfolge. */
    public List<Address> all(PolityView town) {
        return queries.land(town.id()).plots().stream()
                .map(plot -> of(town, plot))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Address::street).thenComparingInt(Address::number))
                .toList();
    }

    /** Wohnsitz eines Spielers oder einer Gemeinde-Polity; leer heißt ohne festen Wohnsitz. */
    public Optional<Address> residenceOf(ActorRef actor) {
        List<Plot> candidates = new ArrayList<>();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            for (Plot plot : queries.land(town.id()).plotsHeldBy(actor)) {
                if (plot.type() == PlotType.RESIDENTIAL) {
                    Optional<Address> address = of(town, plot);
                    if (address.isPresent()) {
                        return address;
                    }
                }
                candidates.add(plot);
            }
        }
        for (Contract lease : queries.contracts().active()) {
            if (lease.kind() == ContractKind.LEASE && lease.payer().equals(actor)) {
                Contract.PlotRef ref = lease.plot().orElseThrow();
                Optional<Address> address = queries.polity(ref.polity()).flatMap(town -> queries.land(town.id())
                        .plot(ref.plot()).flatMap(plot -> of(town, plot)));
                if (address.isPresent()) {
                    return address;
                }
            }
        }
        for (PolityView town : queries.polities()) {
            for (Plot plot : candidates) {
                if (queries.land(town.id()).plot(plot.id()).isPresent()) {
                    Optional<Address> address = of(town, plot);
                    if (address.isPresent()) {
                        return address;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Anschrift an einer Stelle, z. B. wo ein Dorfbewohner zuletzt gesehen wurde. */
    public Optional<Address> at(BlockPosition position) {
        return queries.territory().claimAt(position.chunk()).flatMap(claim -> queries.polity(claim.polity()))
                .flatMap(town -> queries.land(town.id()).plotAt(position.dimension(), position.x(), position.z())
                        .flatMap(plot -> of(town, plot)));
    }

    /** Alle angelegten Straßen der Gemeinde, die höchstens {@link #REACH} Blöcke von der Fläche entfernt sind, nächste zuerst. */
    public static List<Street> streetsNear(PolityView town, Area area) {
        return town.streets().stream()
                .filter(street -> street.area().dimension().equals(area.dimension())
                        && distance(street.area(), area) <= REACH)
                .sorted(Comparator.comparingLong(street -> distance(street.area(), area)))
                .toList();
    }

    private static Optional<Street> nearest(PolityView town, Area area) {
        Street best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Street street : town.streets()) {
            if (!street.area().dimension().equals(area.dimension())) {
                continue;
            }
            long distance = distance(street.area(), area);
            if (distance <= REACH && distance < bestDistance) {
                best = street;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Abstand zweier Rechtecke in Blöcken (größte Achse), 0 bei Überlappung. */
    static long distance(Area one, Area other) {
        long dx = Math.max(0, Math.max(one.minX() - other.maxX(), other.minX() - one.maxX()));
        long dz = Math.max(0, Math.max(one.minZ() - other.maxZ(), other.minZ() - one.maxZ()));
        return Math.max(dx, dz);
    }

    private static double center(Area area, boolean x) {
        return x ? (area.minX() + area.maxX()) / 2.0 : (area.minZ() + area.maxZ()) / 2.0;
    }

    /** Lage quer zur Straße. */
    private static double side(Area area, boolean streetAlongX) {
        return center(area, !streetAlongX);
    }

    /** Lage längs der Straße. */
    private static double along(Area area, boolean streetAlongX) {
        return center(area, streetAlongX);
    }
}
