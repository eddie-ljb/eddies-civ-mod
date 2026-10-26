package de.etiennebader.civ.sim.world;

import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.sim.governance.MunicipalGovernment;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tagesaktuelle Übersicht für Akteure: wie attraktiv jede Gemeinde ist und welche freien
 * Wohngrundstücke sie anbietet. Wird einmal pro Tag erstellt, damit nicht jeder Akteur alle
 * Gemeinden einzeln abfragt.
 *
 * <p>Attraktivität (0,05 bis 1): Grundwert 0,4, plus bis zu 0,3 für freie Grundstücke,
 * plus bis zu 0,2 für Größe, minus bis zu 0,4 für hohe Grundsteuer.
 */
public final class HousingMarket {

    private final Map<PolityId, Double> attraction = new LinkedHashMap<>();
    private final Map<PolityId, Deque<Plot>> freePlots = new LinkedHashMap<>();

    public static HousingMarket survey(SimulationWorld world) {
        HousingMarket market = new HousingMarket();
        long maxTaxRate = Math.max(1, world.scenario().governance().maxTaxRate());
        for (PolityView polity : world.app().queries().polities()) {
            MunicipalGovernment government = world.government(polity.id());
            LandView land = world.app().queries().land(polity.id());
            Deque<Plot> offers = new ArrayDeque<>(government.freeResidentialPlots(land));
            double score = 0.4
                    + 0.3 * Math.min(1, offers.size() / 2.0)
                    + 0.2 * Math.min(1, polity.memberCount() / 10.0)
                    - 0.4 * government.taxRate() / maxTaxRate;
            market.attraction.put(polity.id(), Math.max(0.05, Math.min(1, score)));
            market.freePlots.put(polity.id(), offers);
        }
        return market;
    }

    /** Die höchste Attraktivität aller Gemeinden, 0 wenn es keine gibt. */
    public double bestAttraction() {
        return attraction.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    /** Attraktivität je Gemeinde in Gründungsreihenfolge, für eine deterministische Auswahl. */
    public Map<PolityId, Double> attraction() {
        return Collections.unmodifiableMap(attraction);
    }

    public Optional<Plot> peekFreePlot(PolityId polity) {
        return Optional.ofNullable(freePlots.getOrDefault(polity, new ArrayDeque<>()).peekFirst());
    }

    /** Nimmt ein verkauftes Grundstück aus dem heutigen Angebot. */
    public void markSold(PolityId polity) {
        Deque<Plot> offers = freePlots.get(polity);
        if (offers != null) {
            offers.pollFirst();
        }
    }
}
