package de.etiennebader.civ.sim.scenario;

import java.util.Objects;

/**
 * Alle Parameter einer Simulation, thematisch gruppiert. Wird von {@link ScenarioLoader} aus
 * Properties-Dateien erzeugt und dort vollständig validiert.
 */
public record Scenario(String name, long seed, int days, Population population, World world, Economy economy,
                       Land land, Territory territory, Governance governance, Behavior behavior) {

    public Scenario {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(land, "land");
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(governance, "governance");
        Objects.requireNonNull(behavior, "behavior");
        if (days < 1) {
            throw new IllegalArgumentException("days muss mindestens 1 sein: " + days);
        }
    }

    public Scenario withSeed(long newSeed) {
        return new Scenario(name, newSeed, days, population, world, economy, land, territory, governance, behavior);
    }

    public Scenario withDays(int newDays) {
        return new Scenario(name, seed, newDays, population, world, economy, land, territory, governance, behavior);
    }

    public record Population(int players, int villagers) {

        public int total() {
            return players + villagers;
        }
    }

    public record World(int radiusChunks) {
    }

    public record Economy(long startingWealthMax, long incomePerDay, double incomeSpread, long livingCostPerDay, long savingsTarget,
                          long foundingCapital, double cashShare) {
    }

    public record Land(int plotSizeMin, int plotSizeMax, long plotPricePerBlock, int plotReserve) {
    }

    public record Territory(int claimBase, int claimPerMember, boolean requireAdjacency, int outpostMemberThreshold,
                            int maxOutposts, double outpostReleaseChancePerDay) {
    }

    public record Governance(int taxReviewIntervalDays, long maxTaxRate, long treasuryReservePerMember,
                             long upkeepPerChunkPerDay, double embassyChancePerDay) {
    }

    public record Behavior(double decisionNoise, double belongingGrowthPerDay, double housingGrowthPerDay,
                           double leaveThreshold, double taxTolerance) {
    }
}
