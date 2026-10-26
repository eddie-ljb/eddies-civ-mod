package de.etiennebader.civ.sim.metrics;

import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.sim.agent.Agent;
import de.etiennebader.civ.sim.world.SimulationWorld;

import java.util.List;
import java.util.Objects;

/**
 * Erhebt die Kennzahlen eines Tages aus dem Zustand der Welt.
 */
public final class MetricsCollector {

    private final SimulationWorld world;

    public MetricsCollector(SimulationWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public DailyMetrics collect(DayReport report, long rejectedActions, long events) {
        WorldQueries queries = world.app().queries();
        CurrencyId currency = SimulationWorld.CURRENCY;
        TerritoryView territory = queries.territory();
        LedgerView ledger = queries.ledger();
        List<PolityView> polities = queries.polities();

        int members = 0;
        int homeless = 0;
        int chunks = 0;
        int outposts = 0;
        int plots = 0;
        int privatePlots = 0;
        int embassies = 0;
        long treasuries = 0;
        long taxRateSum = 0;
        for (PolityView polity : polities) {
            PolityRef self = polity.asActor();
            LandView land = queries.land(polity.id());
            members += polity.memberCount();
            for (Membership membership : polity.members()) {
                if (land.plotsHeldBy(membership.member()).isEmpty()) {
                    homeless++;
                }
            }
            chunks += territory.territoryOf(polity.id()).size();
            outposts += territory.countOf(polity.id(), ClaimType.OUTPOST);
            for (Plot plot : land.plots()) {
                plots++;
                if (plot.type() == PlotType.EMBASSY) {
                    embassies++;
                } else if (!plot.isHeldBy(self)) {
                    privatePlots++;
                }
            }
            treasuries += ledger.balanceOf(self, currency).amount();
            taxRateSum += world.government(polity.id()).taxRate();
        }
        int agents = world.agents().size();
        long unaffiliated = world.agents().stream().filter(agent -> !agent.isMember()).count();
        double averageSatisfaction = world.agents().stream()
                .filter(Agent::isMember)
                .mapToDouble(Agent::satisfaction)
                .average()
                .orElse(0);

        return new DailyMetrics(
                report.day().value(), agents, polities.size(), members, (int) unaffiliated, homeless,
                chunks, outposts, plots, privatePlots, embassies,
                ledger.totalSupply(currency).amount(), ledger.totalDeposits(currency).amount(),
                ledger.cashInCirculation(currency).amount(), treasuries,
                report.landTaxCollected(currency).amount(), report.landTaxArrears(currency).amount(),
                polities.isEmpty() ? 0 : (double) taxRateSum / polities.size(), averageSatisfaction,
                rejectedActions, events);
    }
}
