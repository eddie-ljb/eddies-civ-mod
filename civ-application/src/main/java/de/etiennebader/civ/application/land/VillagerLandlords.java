package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.civil.ContractUseCases;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Dorfbewohner als Vermieter (Issue #19): Wer Geld hat, kauft angebotenes Wohngrundstück der eigenen Gemeinde und
 * vermietet es an einen Mitbürger ohne Wohnung. Sparsame kaufen mehr Häuser, Großzügige verlangen weniger Miete.
 *
 * <p>Je Tag kommen höchstens {@value #PER_DAY} Dorfbewohner an die Reihe, und nur ein Teil davon handelt; der Zufall
 * ist fest je Dorfbewohner und Tag. Gebäude errichtet das Spiel nicht; Kauf und Miete sind die wirtschaftliche Seite.
 */
public final class VillagerLandlords {

    public static final int PER_DAY = 100;
    /** Tageswahrscheinlichkeit, dass ein Dorfbewohner überhaupt etwas unternimmt. */
    static final double ACTS = 0.3;
    /** Der Mieter muss die Miete so viele Tage im Voraus bezahlen können. */
    static final long TENANT_RESERVE_DAYS = 5;

    /** Was an einem Tag geschah. */
    public record Report(int purchases, int leases) {
    }

    private final Supplier<WorldQueries> queries;
    private final ContractUseCases contracts;
    private final BuyPlot buyPlot;

    public VillagerLandlords(Supplier<WorldQueries> queries, ContractUseCases contracts, BuyPlot buyPlot) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
        this.buyPlot = Objects.requireNonNull(buyPlot, "buyPlot");
    }

    public Report execute(Day day) {
        Objects.requireNonNull(day, "day");
        int purchases = 0;
        int leases = 0;
        List<PolityView> towns = queries.get().polities().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL)
                .sorted(Comparator.comparing(polity -> polity.id().value())).toList();
        for (PolityView town : towns) {
            List<PersonRef> residents = town.members().stream().map(Membership::member)
                    .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                    .sorted(Comparator.comparing(PersonRef::uuid)).toList();
            int count = Math.min(PER_DAY, residents.size());
            int start = residents.isEmpty() ? 0 : (int) Math.floorMod(day.value() * PER_DAY, (long) residents.size());
            for (int index = 0; index < count; index++) {
                PersonRef landlord = residents.get((start + index) % residents.size());
                if (VillagerAmbitions.roll(landlord, day, 0x90) >= ACTS) {
                    continue;
                }
                try {
                    switch (act(town, landlord, day)) {
                        case 1 -> purchases++;
                        case 2 -> leases++;
                        default -> { }
                    }
                } catch (DomainException | IllegalStateException ignored) {
                    // z. B. kein Geld oder Grundstück schon weg: heute geschieht nichts
                }
            }
        }
        return new Report(purchases, leases);
    }

    /** @return 1 bei Kauf, 2 bei Vermietung, sonst 0 */
    private int act(PolityView town, PersonRef landlord, Day day) {
        WorldQueries world = queries.get();
        Personality personality = Personality.of(landlord);
        List<Plot> owned = world.land(town.id()).plotsHeldBy(landlord).stream()
                .filter(plot -> plot.type() == PlotType.RESIDENTIAL)
                .sorted(Comparator.comparing(plot -> plot.id().value())).toList();
        Money rent = Money.of(rentPerDay(personality), town.currency());
        Optional<Plot> vacant = owned.stream()
                .filter(plot -> world.hasFreeUnit(town, plot.id())).findFirst();
        if (vacant.isPresent()) {
            Optional<PersonRef> tenant = tenantFor(world, town, landlord, rent);
            if (tenant.isPresent()) {
                Contract offer = contracts.offerLease(landlord, town.id(), vacant.get().id(), tenant.get(), rent,
                        Money.zero(town.currency()), 0, day);
                contracts.sign(offer.id(), tenant.get(), day);
                return 2;
            }
            return 0;
        }
        long cash = world.ledger().balanceOf(landlord, town.currency()).amount();
        if (owned.size() >= maxHouses(personality)) {
            return 0;
        }
        Optional<Plot> plot = world.land(town.id()).plots().stream()
                .filter(Plot::isForSale).filter(found -> found.type() == PlotType.RESIDENTIAL)
                .filter(found -> found.askingPrice().orElseThrow().amount() * 2 <= cash)
                .min(Comparator.comparing((Plot found) -> found.askingPrice().orElseThrow().amount())
                        .thenComparing(found -> found.id().value()));
        if (plot.isEmpty()) {
            return 0;
        }
        buyPlot.execute(new BuyPlot.Command(town.id(), plot.get().id(), landlord, day));
        return 1;
    }

    /** Ein Mitbürger ohne Wohnung, der die Miete einige Tage tragen kann. */
    private Optional<PersonRef> tenantFor(WorldQueries world, PolityView town, PersonRef landlord, Money rent) {
        Set<Object> housed = world.contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.LEASE).map(Contract::payer)
                .collect(Collectors.toSet());
        List<PersonRef> candidates = new ArrayList<>();
        for (Membership member : town.members()) {
            if (member.member() instanceof PersonRef villager && !villager.equals(landlord)
                    && !housed.contains(villager)
                    && world.land(town.id()).plotsHeldBy(villager).stream()
                    .noneMatch(plot -> plot.type() == PlotType.RESIDENTIAL)
                    && world.ledger().balanceOf(villager, rent.currency()).amount()
                    >= rent.amount() * TENANT_RESERVE_DAYS) {
                candidates.add(villager);
            }
        }
        return candidates.stream().min(Comparator.comparing(PersonRef::uuid));
    }

    /** Wie viele Häuser ein Dorfbewohner besitzen will: Sparsame bis zu 3, Großzügige nur eines. */
    static int maxHouses(Personality personality) {
        return personality.thrift() >= 40 ? 3 : personality.thrift() >= 0 ? 2 : 1;
    }

    /** Tagesmiete: 10 Smaragdcent, Sparsame verlangen mehr, Großzügige weniger (mindestens 5). */
    static long rentPerDay(Personality personality) {
        return Math.max(5, 10 + personality.thrift() / 10);
    }
}
