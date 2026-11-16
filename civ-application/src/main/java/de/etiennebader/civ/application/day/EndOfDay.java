package de.etiennebader.civ.application.day;

import de.etiennebader.civ.application.federation.Federations;
import de.etiennebader.civ.application.law.EffectiveLaws;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.fiscal.LandTaxCollector;
import de.etiennebader.civ.domain.fiscal.LandTaxReport;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Schließt einen Tag ab: zieht für jede Gemeinde die Grundsteuer ein, in der Reihenfolge,
 * in der das Repository die Gemeinden liefert, und danach die Bundesumlage
 * ({@link StandardParameters#FEDERAL_LEVY_PERCENT}) von den Gliedern jedes Bundes.
 */
public final class EndOfDay {

    private final Aggregates aggregates;
    private final LandTaxCollector landTaxCollector = new LandTaxCollector();

    public EndOfDay(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public DayReport execute(Day day) {
        Objects.requireNonNull(day, "day");
        Ledger ledger = aggregates.ledger();
        List<LandTaxReport> reports = new ArrayList<>();
        List<Polity> polities = List.copyOf(aggregates.repositories().polities().findAll());
        for (Polity polity : polities) {
            LawView laws = new EffectiveLaws(polity, Federations.ancestorsOf(polities, polity.id()));
            reports.add(landTaxCollector.collect(polity, laws, aggregates.land(polity.id()), ledger, day));
        }
        for (Polity federation : polities) {
            if (federation.kind() != PolityKind.FEDERATION) {
                continue;
            }
            long percent = federation.laws().valueOf(StandardParameters.FEDERAL_LEVY_PERCENT);
            if (percent <= 0) {
                continue;
            }
            for (PolityRef constituent : federation.constituents()) {
                Money balance = ledger.balanceOf(constituent, federation.currency());
                Money levy = Money.of(balance.amount() * percent / 100, federation.currency());
                if (levy.isPositive()) {
                    ledger.transfer(constituent, federation.asActor(), levy, TransferReason.LEVY, day);
                }
            }
        }
        aggregates.commit(ledger);
        return new DayReport(day, reports);
    }
}
