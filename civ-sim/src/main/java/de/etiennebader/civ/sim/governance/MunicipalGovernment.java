package de.etiennebader.civ.sim.governance;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.WithdrawEmeralds;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.application.polity.RepealStatute;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.application.territory.ReleaseChunk;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.fiscal.LandTaxReport;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.sim.scenario.Scenario;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Die Verwaltung einer Gemeinde. Sie zahlt täglich den Unterhalt ihres Gebiets und verfolgt drei Ziele:
 * <ol>
 *     <li><b>Wohnraum:</b> immer etwas mehr freie Wohngrundstücke als Mitglieder ohne Grundstück.
 *         Reicht das Gebiet nicht, beansprucht sie einen angrenzenden Chunk. Überzähliges Bauland,
 *         etwa nach Austritten, löst sie wieder auf.</li>
 *     <li><b>Haushalt:</b> eine Rücklage pro Mitglied. Liegt die Kasse darunter, steigt die Grundsteuer;
 *         liegt sie weit darüber oder zahlen viele nicht, sinkt sie.</li>
 *     <li><b>Außenbeziehungen:</b> ab einer Mindestgröße Außenposten erlauben und gelegentlich anderen
 *         Gemeinden eine Botschaft einrichten.</li>
 * </ol>
 * Abgelehnte Vorhaben meldet sie an {@code rejections} und versucht es an einem anderen Tag erneut.
 */
public final class MunicipalGovernment {

    private static final int EMBASSY_SIZE = 4;
    private static final int MAX_PLOTS_PER_DAY = 3;
    private static final int SURPLUS_TOLERANCE = 2;
    private static final String TAX_LAW_TITLE = "Grundsteuerordnung";
    private static final String OUTPOST_LAW_TITLE = "Außenpostengesetz";

    private final PolityId polity;
    private final CivApplication app;
    private final Scenario scenario;
    private final RandomGenerator random;
    private final Consumer<DomainException> rejections;
    private final PlotFinder plotFinder = new PlotFinder();
    private StatuteId taxStatute;
    private long taxRate;
    private boolean outpostsAllowed;

    public MunicipalGovernment(PolityId polity, CivApplication app, Scenario scenario, RandomGenerator random,
                               Consumer<DomainException> rejections) {
        this.polity = Objects.requireNonNull(polity, "polity");
        this.app = Objects.requireNonNull(app, "app");
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        this.random = Objects.requireNonNull(random, "random");
        this.rejections = Objects.requireNonNull(rejections, "rejections");
    }

    public PolityId polity() {
        return polity;
    }

    /** Aktueller Grundsteuersatz pro Block und Tag. */
    public long taxRate() {
        return taxRate;
    }

    /**
     * Ein Verwaltungstag.
     *
     * @param lastTaxReport der Grundsteuerbericht des Vortags, falls es einen gibt
     */
    public void govern(Day day, Optional<LandTaxReport> lastTaxReport) {
        PolityView view = app.queries().polity(polity).orElseThrow();
        payUpkeep(view, day);
        provideHousing(view, day);
        allowOutpostsWhenLargeEnough(view, day);
        if (day.value() % scenario.governance().taxReviewIntervalDays() == 0) {
            reviewTaxRate(view, lastTaxReport, day);
        }
        if (random.nextDouble() < scenario.governance().embassyChancePerDay()) {
            openEmbassyForNeighbour(day);
        }
        if (random.nextDouble() < scenario.territory().outpostReleaseChancePerDay()) {
            releaseEmptyOutpost(day);
        }
    }

    /** Unterhalt pro Chunk, soweit die Kasse reicht. Wird als Smaragde ausgegeben und verlässt den Geldkreislauf. */
    private void payUpkeep(PolityView view, Day day) {
        long due = app.queries().territory().territoryOf(polity).size() * scenario.governance().upkeepPerChunkPerDay();
        long treasury = app.queries().ledger().balanceOf(view.asActor(), view.currency()).amount();
        long payable = Math.min(due, treasury);
        if (payable > 0) {
            app.withdrawEmeralds().execute(new WithdrawEmeralds.Command(view.asActor(), Money.of(payable, view.currency()), day));
        }
    }

    private void provideHousing(PolityView view, Day day) {
        LandView land = app.queries().land(polity);
        long homeless = view.members().stream()
                .filter(membership -> land.plotsHeldBy(membership.member()).isEmpty())
                .count();
        List<Plot> freePlots = freeResidentialPlots(land);
        long wanted = homeless + scenario.land().plotReserve();
        if (freePlots.size() > wanted + SURPLUS_TOLERANCE) {
            dissolveSurplus(freePlots, freePlots.size() - wanted, day);
            return;
        }
        long available = freePlots.size();
        for (int created = 0; available < wanted && created < MAX_PLOTS_PER_DAY; created++) {
            if (!createResidentialPlot(day) && !(expandCore(day) && createResidentialPlot(day))) {
                return;
            }
            available++;
        }
    }

    /** Löst die zuletzt angelegten überzähligen Grundstücke auf, höchstens einige pro Tag. */
    private void dissolveSurplus(List<Plot> freePlots, long surplus, Day day) {
        long count = Math.min(surplus, MAX_PLOTS_PER_DAY);
        for (int index = freePlots.size() - 1; index >= freePlots.size() - count; index--) {
            PlotId plot = freePlots.get(index).id();
            attempt(() -> app.dissolvePlot().execute(new DissolvePlot.Command(polity, plot, day)));
        }
    }

    /** Wohngrundstücke, die noch der Gemeinde gehören und verkauft werden können. */
    public List<Plot> freeResidentialPlots(LandView land) {
        PolityRef self = new PolityRef(polity);
        return land.plots().stream()
                .filter(plot -> plot.type() == PlotType.RESIDENTIAL && plot.isHeldBy(self))
                .toList();
    }

    private boolean createResidentialPlot(Day day) {
        int width = randomPlotEdge();
        int depth = randomPlotEdge();
        Optional<Area> area = plotFinder.find(app.queries().land(polity),
                app.queries().territory().territoryOf(polity), width, depth);
        return area.isPresent()
                && attempt(() -> app.createPlot().execute(new CreatePlot.Command(polity, area.get(), PlotType.RESIDENTIAL, day)));
    }

    private boolean expandCore(Day day) {
        TerritoryView territory = app.queries().territory();
        List<ChunkCoord> candidates = Chunks.sorted(territory.territoryOf(polity)).stream()
                .filter(chunk -> territory.claimAt(chunk).orElseThrow().type() == ClaimType.CORE)
                .flatMap(chunk -> Chunks.sorted(chunk.neighbours()).stream())
                .filter(chunk -> territory.claimAt(chunk).isEmpty())
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        ChunkCoord target = candidates.get(random.nextInt(candidates.size()));
        return attempt(() -> app.claimChunk().execute(new ClaimChunk.Command(polity, target, ClaimType.CORE, day)));
    }

    private void allowOutpostsWhenLargeEnough(PolityView view, Day day) {
        if (outpostsAllowed || scenario.territory().maxOutposts() == 0
                || view.memberCount() < scenario.territory().outpostMemberThreshold()) {
            return;
        }
        outpostsAllowed = attempt(() -> app.enactStatute().execute(new EnactStatute.Command(polity, OUTPOST_LAW_TITLE,
                List.of(ParameterValue.of(StandardParameters.MAX_OUTPOSTS, scenario.territory().maxOutposts())), day)));
        if (outpostsAllowed) {
            claimOutpost(day);
        }
    }

    private void claimOutpost(Day day) {
        int radius = scenario.world().radiusChunks();
        ChunkCoord home = Chunks.sorted(app.queries().territory().territoryOf(polity)).getFirst();
        ChunkCoord target = new ChunkCoord(home.dimension(),
                home.x() + random.nextInt(-radius, radius + 1), home.z() + random.nextInt(-radius, radius + 1));
        if (app.queries().territory().claimAt(target).isEmpty()) {
            attempt(() -> app.claimChunk().execute(new ClaimChunk.Command(polity, target, ClaimType.OUTPOST, day)));
        }
    }

    private void releaseEmptyOutpost(Day day) {
        TerritoryView territory = app.queries().territory();
        LandView land = app.queries().land(polity);
        Chunks.sorted(territory.territoryOf(polity)).stream()
                .filter(chunk -> territory.claimAt(chunk).orElseThrow().type() == ClaimType.OUTPOST)
                .filter(chunk -> !land.hasPlotsIn(chunk))
                .findFirst()
                .ifPresent(chunk -> attempt(() -> app.releaseChunk().execute(new ReleaseChunk.Command(polity, chunk, day))));
    }

    private void reviewTaxRate(PolityView view, Optional<LandTaxReport> lastTaxReport, Day day) {
        long treasury = app.queries().ledger().balanceOf(view.asActor(), view.currency()).amount();
        long target = view.memberCount() * scenario.governance().treasuryReservePerMember();
        double arrearsShare = lastTaxReport.map(MunicipalGovernment::arrearsShare).orElse(0.0);
        long newRate = taxRate;
        if (arrearsShare > 0.3 || treasury > 2 * target) {
            newRate = Math.max(0, taxRate - 1);
        } else if (treasury < target) {
            newRate = Math.min(scenario.governance().maxTaxRate(), taxRate + 1);
        }
        if (newRate != taxRate) {
            changeTaxRate(newRate, day);
        }
    }

    private static double arrearsShare(LandTaxReport report) {
        long arrears = report.totalArrears().amount();
        long due = arrears + report.collected().amount();
        return due == 0 ? 0 : (double) arrears / due;
    }

    private void changeTaxRate(long newRate, Day day) {
        if (taxStatute != null) {
            StatuteId previous = taxStatute;
            if (!attempt(() -> app.repealStatute().execute(new RepealStatute.Command(polity, previous, day)))) {
                return;
            }
            taxStatute = null;
            taxRate = 0;
        }
        if (newRate > 0) {
            try {
                taxStatute = app.enactStatute().execute(new EnactStatute.Command(polity, TAX_LAW_TITLE,
                        List.of(ParameterValue.of(StandardParameters.LAND_TAX_PER_BLOCK, newRate)), day));
                taxRate = newRate;
            } catch (DomainException rejected) {
                rejections.accept(rejected);
            }
        }
    }

    private void openEmbassyForNeighbour(Day day) {
        List<PolityView> others = app.queries().polities().stream()
                .filter(other -> !other.id().equals(polity))
                .toList();
        if (others.isEmpty()) {
            return;
        }
        PolityRef guest = others.get(random.nextInt(others.size())).asActor();
        LandView land = app.queries().land(polity);
        if (!land.plotsHeldBy(guest).isEmpty()) {
            return;
        }
        Optional<Area> area = plotFinder.find(land, app.queries().territory().territoryOf(polity), EMBASSY_SIZE, EMBASSY_SIZE);
        if (area.isEmpty()) {
            return;
        }
        try {
            PlotId embassy = app.createPlot().execute(new CreatePlot.Command(polity, area.get(), PlotType.EMBASSY, day));
            app.assignPlot().execute(new AssignPlot.Command(polity, embassy, guest, day));
        } catch (DomainException rejected) {
            rejections.accept(rejected);
        }
    }

    private int randomPlotEdge() {
        return random.nextInt(scenario.land().plotSizeMin(), scenario.land().plotSizeMax() + 1);
    }

    private boolean attempt(Runnable action) {
        try {
            action.run();
            return true;
        } catch (DomainException rejected) {
            rejections.accept(rejected);
            return false;
        }
    }
}
