package de.etiennebader.civ.application.day;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.fiscal.LandTaxCollector;
import de.etiennebader.civ.domain.fiscal.LandTaxReport;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Schließt einen Tag ab: zieht für jede Gemeinde die Grundsteuer ein, in der Reihenfolge,
 * in der das Repository die Gemeinden liefert.
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
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            reports.add(landTaxCollector.collect(polity, aggregates.land(polity.id()), ledger, day));
        }
        aggregates.commit(ledger);
        return new DayReport(day, reports);
    }
}
