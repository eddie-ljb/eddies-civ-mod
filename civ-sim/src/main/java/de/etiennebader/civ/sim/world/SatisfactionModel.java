package de.etiennebader.civ.sim.world;

import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.fiscal.LandTaxReport;
import de.etiennebader.civ.domain.fiscal.TaxArrear;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.sim.agent.Agent;
import de.etiennebader.civ.sim.scenario.Scenario;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Wie zufrieden Mitglieder nach dem Steuertag mit ihrer Gemeinde sind.
 *
 * <p>Pro Tag: +0,01 Grundstimmung, minus bis zu 0,03 × (Steuerlast / tolerierte Steuerlast),
 * −0,01 × Wohnbedürfnis ohne Grundstück, −0,05 bei Steuerschulden. Nichtmitglieder sind
 * nicht betroffen; beim Beitritt startet die Zufriedenheit neu.
 */
public final class SatisfactionModel {

    private static final double DAILY_BASE = 0.01;
    private static final double TAX_WEIGHT = 0.03;
    private static final double HOUSING_WEIGHT = 0.01;
    private static final double ARREAR_PENALTY = 0.05;

    private final SimulationWorld world;
    private final Scenario scenario;

    public SatisfactionModel(SimulationWorld world) {
        this.world = Objects.requireNonNull(world, "world");
        this.scenario = world.scenario();
    }

    public void apply(DayReport report) {
        Set<ActorRef> debtors = new HashSet<>();
        for (LandTaxReport taxReport : report.landTaxReports()) {
            taxReport.arrears().stream().map(TaxArrear::debtor).forEach(debtors::add);
        }
        for (Agent agent : world.agents()) {
            agent.home().ifPresent(home -> update(agent, home, debtors.contains(agent.actor())));
        }
    }

    private void update(Agent agent, PolityId home, boolean inArrears) {
        PolityView polity = world.app().queries().polity(home).orElseThrow();
        LandView land = world.app().queries().land(home);
        long rate = polity.laws().valueOf(StandardParameters.LAND_TAX_PER_BLOCK);
        long taxableArea = land.plotsHeldBy(agent.actor()).stream().mapToLong(plot -> plot.area().size()).sum();
        long income = Math.max(1, agent.dailyIncome(scenario.economy().incomePerDay()));
        double burden = (double) rate * taxableArea / income;

        double delta = DAILY_BASE - TAX_WEIGHT * Math.min(3, burden / scenario.behavior().taxTolerance());
        if (taxableArea == 0) {
            delta -= HOUSING_WEIGHT * agent.needs().housing();
        }
        if (inArrears) {
            delta -= ARREAR_PENALTY;
        }
        agent.adjustSatisfaction(delta);
    }
}
