package de.etiennebader.civ.domain.fiscal;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;

/**
 * Ergebnis eines Grundsteuereinzugs: eingezogener Betrag und offene Schulden.
 */
public record LandTaxReport(PolityId polity, Day day, Money collected, List<TaxArrear> arrears) {

    public LandTaxReport {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(collected, "collected");
        arrears = List.copyOf(Objects.requireNonNull(arrears, "arrears"));
        if (collected.isNegative()) {
            throw new IllegalArgumentException("Eingezogener Betrag darf nicht negativ sein: " + collected);
        }
    }

    /** Ein Report ohne Einnahmen und Schulden, z. B. bei einem Steuersatz von 0. */
    public static LandTaxReport empty(PolityId polity, CurrencyId currency, Day day) {
        return new LandTaxReport(polity, day, Money.zero(currency), List.of());
    }

    /** Summe aller offenen Schulden aus diesem Einzug. */
    public Money totalArrears() {
        return arrears.stream()
                .map(TaxArrear::amount)
                .reduce(Money.zero(collected.currency()), Money::plus);
    }
}
