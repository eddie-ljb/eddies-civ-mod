package de.etiennebader.civ.sim.world;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.DepositCash;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawCash;
import de.etiennebader.civ.application.economy.WithdrawEmeralds;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.sim.agent.Agent;
import de.etiennebader.civ.sim.agent.AgentAction;
import de.etiennebader.civ.sim.agent.AgentKind;
import de.etiennebader.civ.sim.agent.AgentSituation;
import de.etiennebader.civ.sim.scenario.Scenario;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Der Tagesablauf eines Akteurs: Einkommen und Lebenshaltung, Lagebeurteilung und das Ausführen
 * der gewählten Handlung über die Use Cases.
 */
public final class AgentRoutine {

    private static final int FOUNDING_LOCATION_ATTEMPTS = 30;
    private static final double RESIDENT_SHARE_OF_VILLAGERS = 0.3;
    private static final double MISSED_LIVING_COST_PENALTY = 0.02;

    private final SimulationWorld world;
    private final CivApplication app;
    private final Scenario scenario;

    public AgentRoutine(SimulationWorld world) {
        this.world = Objects.requireNonNull(world, "world");
        this.app = world.app();
        this.scenario = world.scenario();
    }

    /** Zahlt einem Akteur vor dem ersten Tag ein zufälliges Startvermögen ein. */
    public void grantStartingWealth(Agent agent) {
        long max = scenario.economy().startingWealthMax();
        long wealth = max == 0 ? 0 : world.random().nextLong(max + 1);
        if (wealth > 0) {
            app.depositEmeralds().execute(new DepositEmeralds.Command(agent.actor(), money(wealth), Day.ZERO));
        }
    }

    /**
     * Einkommen als Smaragd-Einzahlung, danach Lebenshaltung: zuerst vom Konto, fehlt dort Geld,
     * wird Bargeld eingezahlt. Reicht beides nicht, sinkt die Zufriedenheit.
     */
    public void earnAndSpend(Agent agent, Day day) {
        long income = agent.dailyIncome(scenario.economy().incomePerDay());
        if (income > 0) {
            app.depositEmeralds().execute(new DepositEmeralds.Command(agent.actor(), money(income), day));
        }
        long cost = scenario.economy().livingCostPerDay();
        if (cost == 0) {
            return;
        }
        long shortfall = cost - balanceOf(agent);
        if (shortfall > 0 && agent.cashOnHand() > 0) {
            depositCash(agent, Math.min(shortfall, agent.cashOnHand()), day);
        }
        long payable = Math.min(cost, balanceOf(agent));
        if (payable > 0) {
            app.withdrawEmeralds().execute(new WithdrawEmeralds.Command(agent.actor(), money(payable), day));
        }
        if (payable < cost) {
            agent.adjustSatisfaction(-MISSED_LIVING_COST_PENALTY);
        }
    }

    /** Aktualisiert die Bedürfnisse und stellt die Fakten für die Entscheidung zusammen. */
    public AgentSituation assess(Agent agent, HousingMarket market) {
        long balance = balanceOf(agent);
        Optional<PolityId> home = agent.home();
        boolean homeless = home.isPresent() && app.queries().land(home.get()).plotsHeldBy(agent.actor()).isEmpty();
        agent.needs().update(home.isPresent(), homeless, balance,
                agent.savingsTarget(scenario.economy().savingsTarget()),
                scenario.behavior().belongingGrowthPerDay() * (0.5 + agent.personality().sociability()),
                scenario.behavior().housingGrowthPerDay());

        boolean soleMember = home.map(id -> app.queries().polity(id).orElseThrow().memberCount() == 1).orElse(false);
        boolean plotAffordable = home.flatMap(market::peekFreePlot)
                .map(plot -> priceOf(plot) <= balance)
                .orElse(false);
        long desiredCash = Math.round(scenario.economy().cashShare() * agent.personality().cashAffinity()
                * (balance + agent.cashOnHand()));
        return new AgentSituation(home.isPresent(), soleMember, homeless, plotAffordable,
                balance >= scenario.economy().foundingCapital(), market.bestAttraction(),
                desiredCash - agent.cashOnHand(), desiredCash, world.agents().size() > 1,
                scenario.behavior().leaveThreshold());
    }

    /**
     * Führt eine Handlung aus. Lehnt der Kern sie ab, wird das gezählt und der Tag geht weiter.
     */
    public void perform(Agent agent, AgentAction action, HousingMarket market, AgentSituation situation, Day day) {
        try {
            switch (action) {
                case REST -> {
                }
                case FOUND_POLITY -> foundPolity(agent, day);
                case JOIN_POLITY -> joinBestPolity(agent, market, day);
                case LEAVE_POLITY -> leavePolity(agent, day);
                case BUY_PLOT -> buyPlot(agent, market, day);
                case WITHDRAW_CASH -> withdrawCash(agent, situation.cashGap(), day);
                case DEPOSIT_CASH -> depositCash(agent, cashToDeposit(agent, situation), day);
                case PAY_IN_CASH -> payInCash(agent);
            }
        } catch (DomainException rejected) {
            world.rejections().accept(rejected);
        }
    }

    private void foundPolity(Agent agent, Day day) {
        Optional<ChunkCoord> location = freeFoundingLocation();
        if (location.isEmpty()) {
            return;
        }
        PolityId polity = app.foundPolity().execute(new FoundPolity.Command(
                PolityName.of(world.nextPolityName()), agent.actor(), SimulationWorld.CURRENCY, location.get(), day));
        world.establishGovernment(polity);
        agent.moveTo(polity);
        app.transferFunds().execute(new TransferFunds.Command(agent.actor(), new PolityRef(polity),
                money(scenario.economy().foundingCapital()), TransferReason.GRANT, day));
    }

    /** Ein freier Chunk, dessen acht Nachbarn ebenfalls frei sind, damit Gemeinden Platz zum Wachsen haben. */
    private Optional<ChunkCoord> freeFoundingLocation() {
        TerritoryView territory = app.queries().territory();
        int radius = scenario.world().radiusChunks();
        for (int attempt = 0; attempt < FOUNDING_LOCATION_ATTEMPTS; attempt++) {
            int x = world.random().nextInt(-radius, radius + 1);
            int z = world.random().nextInt(-radius, radius + 1);
            if (isFreeArea(territory, x, z)) {
                return Optional.of(new ChunkCoord(DimensionId.OVERWORLD, x, z));
            }
        }
        return Optional.empty();
    }

    private static boolean isFreeArea(TerritoryView territory, int centerX, int centerZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (territory.claimAt(new ChunkCoord(DimensionId.OVERWORLD, centerX + dx, centerZ + dz)).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Wählt die Gemeinde mit der höchsten Attraktivität plus persönlichem Zufall. */
    private void joinBestPolity(Agent agent, HousingMarket market, Day day) {
        PolityId choice = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<PolityId, Double> entry : market.attraction().entrySet()) {
            double score = entry.getValue() + scenario.behavior().decisionNoise() * world.random().nextDouble();
            if (score > bestScore) {
                bestScore = score;
                choice = entry.getKey();
            }
        }
        if (choice == null) {
            return;
        }
        MembershipType type = agent.kind() == AgentKind.VILLAGER && world.random().nextDouble() < RESIDENT_SHARE_OF_VILLAGERS
                ? MembershipType.RESIDENT
                : MembershipType.CITIZEN;
        app.joinPolity().execute(new JoinPolity.Command(choice, agent.actor(), type, day));
        agent.moveTo(choice);
    }

    /** Verkauft eigene Grundstücke zum halben Preis an die Gemeinde zurück, sofern ihre Kasse reicht, und tritt aus. */
    private void leavePolity(Agent agent, Day day) {
        PolityId polity = agent.home().orElseThrow();
        PolityView view = app.queries().polity(polity).orElseThrow();
        LandView land = app.queries().land(polity);
        for (Plot plot : List.copyOf(land.plotsHeldBy(agent.actor()))) {
            long treasury = app.queries().ledger().balanceOf(view.asActor(), SimulationWorld.CURRENCY).amount();
            long price = priceOf(plot) / 2 <= treasury ? priceOf(plot) / 2 : 0;
            app.sellPlot().execute(new SellPlot.Command(polity, plot.id(), agent.actor(), view.asActor(), money(price), day));
        }
        app.leavePolity().execute(new LeavePolity.Command(polity, agent.actor(), day));
        agent.becomeHomeless();
    }

    private void buyPlot(Agent agent, HousingMarket market, Day day) {
        PolityId polity = agent.home().orElseThrow();
        Optional<Plot> offer = market.peekFreePlot(polity);
        if (offer.isEmpty()) {
            return;
        }
        Plot plot = offer.get();
        market.markSold(polity);
        app.sellPlot().execute(new SellPlot.Command(polity, plot.id(), new PolityRef(polity), agent.actor(),
                money(priceOf(plot)), day));
    }

    private void withdrawCash(Agent agent, long gap, Day day) {
        long amount = Math.min(gap, balanceOf(agent));
        if (amount <= 0) {
            return;
        }
        app.withdrawCash().execute(new WithdrawCash.Command(agent.actor(), money(amount), day));
        agent.receiveCash(amount);
    }

    private void depositCash(Agent agent, long amount, Day day) {
        if (amount <= 0) {
            return;
        }
        app.depositCash().execute(new DepositCash.Command(agent.actor(), money(amount), day));
        agent.spendCash(amount);
    }

    /** Überschuss über den Wunschbestand; bei großer Geldsorge alles. */
    private static long cashToDeposit(Agent agent, AgentSituation situation) {
        if (agent.needs().security() > 0.8) {
            return agent.cashOnHand();
        }
        return Math.min(agent.cashOnHand(), Math.max(0, -situation.cashGap()));
    }

    /** Gibt einem zufälligen anderen Akteur Bargeld. Der Kern erfährt davon nichts. */
    private void payInCash(Agent agent) {
        List<Agent> agents = world.agents();
        Agent partner = agents.get(world.random().nextInt(agents.size()));
        if (partner == agent || agent.cashOnHand() == 0) {
            return;
        }
        long limit = Math.max(1, Math.min(agent.cashOnHand(), scenario.economy().incomePerDay()));
        long amount = 1 + world.random().nextLong(limit);
        agent.spendCash(amount);
        partner.receiveCash(amount);
    }

    private long priceOf(Plot plot) {
        return Math.multiplyExact(plot.area().size(), scenario.land().plotPricePerBlock());
    }

    private long balanceOf(Agent agent) {
        return app.queries().ledger().balanceOf(agent.actor(), SimulationWorld.CURRENCY).amount();
    }

    private static Money money(long amount) {
        return Money.of(amount, SimulationWorld.CURRENCY);
    }
}
