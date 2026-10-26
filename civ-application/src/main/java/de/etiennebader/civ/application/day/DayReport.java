package de.etiennebader.civ.application.day;

import de.etiennebader.civ.domain.fiscal.LandTaxReport;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.List;
import java.util.Objects;

/**
 * Ergebnis eines Tageswechsels: ein Grundsteuerbericht je Gemeinde, in fester Reihenfolge.
 */
public record DayReport(Day day, List<LandTaxReport> landTaxReports) {

    public DayReport {
        Objects.requireNonNull(day, "day");
        landTaxReports = List.copyOf(Objects.requireNonNull(landTaxReports, "landTaxReports"));
    }

    /** Eingezogene Grundsteuer aller Gemeinden mit dieser Währung. */
    public Money landTaxCollected(CurrencyId currency) {
        return landTaxReports.stream()
                .map(LandTaxReport::collected)
                .filter(amount -> amount.currency().equals(currency))
                .reduce(Money.zero(currency), Money::plus);
    }

    /** Neu entstandene Grundsteuerschulden aller Gemeinden mit dieser Währung. */
    public Money landTaxArrears(CurrencyId currency) {
        return landTaxReports.stream()
                .map(LandTaxReport::totalArrears)
                .filter(amount -> amount.currency().equals(currency))
                .reduce(Money.zero(currency), Money::plus);
    }
}
