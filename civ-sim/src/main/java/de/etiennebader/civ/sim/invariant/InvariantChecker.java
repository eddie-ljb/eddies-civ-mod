package de.etiennebader.civ.sim.invariant;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.sim.agent.Agent;
import de.etiennebader.civ.sim.governance.Chunks;
import de.etiennebader.civ.sim.world.SimulationWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prüft nach jedem Tag die Invarianten über alle Aggregate hinweg:
 * <ol>
 *     <li>Geldmengenerhaltung: Konten + Bargeld = Geldmenge</li>
 *     <li>Das Bargeld in den Taschen aller Akteure entspricht dem umlaufenden Bargeld</li>
 *     <li>Kein Konto ist negativ</li>
 *     <li>Die Zugehörigkeit der Akteure stimmt mit dem Kern überein; niemand gehört zwei Gemeinden an</li>
 *     <li>Jede Gemeinde hat mindestens ein Mitglied</li>
 *     <li>Grundstücke liegen im Gebiet ihrer Gemeinde und überlappen nicht</li>
 *     <li>Das Kerngebiet jeder Gemeinde ist zusammenhängend, falls der Server das verlangt</li>
 * </ol>
 */
public final class InvariantChecker {

    private final SimulationWorld world;

    public InvariantChecker(SimulationWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    /**
     * @throws InvariantViolationException mit allen Verstößen des Tages
     */
    public void verify(Day day) {
        List<String> violations = new ArrayList<>();
        WorldQueries queries = world.app().queries();
        checkMoney(queries.ledger(), queries.polities(), violations);
        checkMemberships(queries, violations);
        TerritoryView territory = queries.territory();
        for (PolityView polity : queries.polities()) {
            checkPlots(polity.id(), queries.land(polity.id()), territory, violations);
            if (world.scenario().territory().requireAdjacency()) {
                checkCoreContiguity(polity.id(), territory, violations);
            }
        }
        if (!violations.isEmpty()) {
            throw new InvariantViolationException(day, violations);
        }
    }

    private void checkMoney(LedgerView ledger, List<PolityView> polities, List<String> violations) {
        CurrencyId currency = SimulationWorld.CURRENCY;
        long supply = ledger.totalSupply(currency).amount();
        long deposits = ledger.totalDeposits(currency).amount();
        long cash = ledger.cashInCirculation(currency).amount();
        if (deposits + cash != supply) {
            violations.add("Geldmenge %d ≠ Konten %d + Bargeld %d".formatted(supply, deposits, cash));
        }
        long cashInPockets = world.agents().stream().mapToLong(Agent::cashOnHand).sum();
        if (cashInPockets != cash) {
            violations.add("Bargeld in Taschen %d ≠ umlaufendes Bargeld %d".formatted(cashInPockets, cash));
        }
        List<ActorRef> accounts = new ArrayList<>();
        world.agents().forEach(agent -> accounts.add(agent.actor()));
        polities.forEach(polity -> accounts.add(polity.asActor()));
        for (ActorRef account : accounts) {
            if (ledger.balanceOf(account, currency).isNegative()) {
                violations.add("Negativer Kontostand: " + account);
            }
        }
    }

    private void checkMemberships(WorldQueries queries, List<String> violations) {
        Map<ActorRef, List<PolityId>> membershipsByActor = new HashMap<>();
        for (PolityView polity : queries.polities()) {
            if (polity.memberCount() < 1) {
                violations.add("Gemeinde ohne Mitglieder: " + polity.name());
            }
            polity.members().forEach(membership -> membershipsByActor
                    .computeIfAbsent(membership.member(), actor -> new ArrayList<>())
                    .add(polity.id()));
        }
        for (Agent agent : world.agents()) {
            List<PolityId> memberships = membershipsByActor.getOrDefault(agent.actor(), List.of());
            if (memberships.size() > 1) {
                violations.add(agent + " gehört mehreren Gemeinden an: " + memberships);
            }
            Optional<PolityId> actual = memberships.stream().findFirst();
            if (!actual.equals(agent.home())) {
                violations.add(agent + ": Simulation erwartet " + agent.home() + ", Kern meldet " + actual);
            }
        }
    }

    private static void checkPlots(PolityId polity, LandView land, TerritoryView territory, List<String> violations) {
        List<Plot> plots = land.plots().stream()
                .sorted(Comparator.comparingInt((Plot plot) -> plot.area().minX()))
                .toList();
        for (Plot plot : plots) {
            for (ChunkCoord chunk : plot.area().chunks()) {
                if (!territory.sovereignOf(chunk).equals(Optional.of(polity))) {
                    violations.add("Grundstück " + plot.id().value() + " liegt außerhalb des Gebiets in " + chunk);
                }
            }
        }
        for (int i = 0; i < plots.size(); i++) {
            Area area = plots.get(i).area();
            for (int j = i + 1; j < plots.size() && plots.get(j).area().minX() <= area.maxX(); j++) {
                if (area.overlaps(plots.get(j).area())) {
                    violations.add("Grundstücke überlappen: " + plots.get(i).id().value() + " und " + plots.get(j).id().value());
                }
            }
        }
    }

    private static void checkCoreContiguity(PolityId polity, TerritoryView territory, List<String> violations) {
        Set<ChunkCoord> core = territory.territoryOf(polity).stream()
                .filter(chunk -> territory.claimAt(chunk).orElseThrow().type() == ClaimType.CORE)
                .collect(Collectors.toSet());
        if (core.isEmpty()) {
            return;
        }
        Set<ChunkCoord> reached = new HashSet<>();
        Deque<ChunkCoord> open = new ArrayDeque<>(List.of(Chunks.sorted(core).getFirst()));
        while (!open.isEmpty()) {
            ChunkCoord chunk = open.pop();
            if (reached.add(chunk)) {
                chunk.neighbours().stream().filter(core::contains).forEach(open::push);
            }
        }
        if (reached.size() != core.size()) {
            violations.add("Kerngebiet von " + polity.value() + " ist nicht zusammenhängend");
        }
    }
}
