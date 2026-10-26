package de.etiennebader.civ.domain.fiscal;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.economy.TransferResult;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Zieht die tägliche Grundsteuer einer Polity ein. Zustandsloser Domain Service.
 *
 * <p>Ablauf:
 * <ol>
 *     <li>Satz {@link StandardParameters#LAND_TAX_PER_BLOCK} lesen; bei 0 endet der Einzug sofort.</li>
 *     <li>Steuerfrei sind Grundstücke der Polity selbst (Gemeindeland) und Botschaften,
 *         solange {@link StandardParameters#TAX_EMBASSIES} nicht gesetzt ist.</li>
 *     <li>Je Inhaber: Betrag = Satz × Fläche aller seiner Grundstücke, in der Währung der Polity.</li>
 *     <li>Einzug immer vom Konto per {@link Ledger#tryTransfer}; fehlende Deckung wird zur
 *         {@link TaxArrear}. Bargeld zählt nicht.</li>
 * </ol>
 * Inhaber werden in der festen Reihenfolge {@link ActorRef#CANONICAL_ORDER} verarbeitet,
 * damit die Simulation deterministisch bleibt.
 */
public final class LandTaxCollector {

    public LandTaxReport collect(Polity polity, LandView land, Ledger ledger, Day day) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(land, "land");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(day, "day");
        if (!land.owner().equals(polity.id())) {
            throw new IllegalArgumentException("Grundbuch gehört nicht zu " + polity);
        }

        LawView laws = polity.laws();
        long ratePerBlock = laws.valueOf(StandardParameters.LAND_TAX_PER_BLOCK);
        if (ratePerBlock == 0) {
            return LandTaxReport.empty(polity.id(), polity.currency(), day);
        }
        boolean taxEmbassies = laws.valueOf(StandardParameters.TAX_EMBASSIES);

        Money collected = Money.zero(polity.currency());
        List<TaxArrear> arrears = new ArrayList<>();
        for (Map.Entry<ActorRef, Long> entry : taxableAreaByHolder(polity, land, taxEmbassies).entrySet()) {
            ActorRef holder = entry.getKey();
            Money due = Money.of(Math.multiplyExact(ratePerBlock, entry.getValue()), polity.currency());
            TransferResult result = ledger.tryTransfer(holder, polity.asActor(), due, TransferReason.LAND_TAX, day);
            switch (result) {
                case TransferResult.Completed completed -> collected = collected.plus(completed.event().amount());
                case TransferResult.Rejected ignored -> arrears.add(new TaxArrear(holder, due));
            }
        }
        return new LandTaxReport(polity.id(), day, collected, arrears);
    }

    private static Map<ActorRef, Long> taxableAreaByHolder(Polity polity, LandView land, boolean taxEmbassies) {
        Map<ActorRef, Long> areaByHolder = new TreeMap<>(ActorRef.CANONICAL_ORDER);
        for (Plot plot : land.plots()) {
            if (isTaxable(plot, polity, taxEmbassies)) {
                areaByHolder.merge(plot.holder(), plot.area().size(), Math::addExact);
            }
        }
        return areaByHolder;
    }

    private static boolean isTaxable(Plot plot, Polity polity, boolean taxEmbassies) {
        if (plot.isHeldBy(polity.asActor())) {
            return false;
        }
        return plot.type() != PlotType.EMBASSY || taxEmbassies;
    }
}
