package de.etiennebader.civ.sim.metrics;

import java.util.List;
import java.util.Locale;

/**
 * Kennzahlen eines Simulationstags; eine Zeile der CSV-Datei. Beträge in kleinster Währungseinheit.
 */
public record DailyMetrics(
        long day,
        int agents,
        int polities,
        int members,
        int unaffiliated,
        int homelessMembers,
        int chunks,
        int outposts,
        int plots,
        int privatePlots,
        int embassies,
        long moneySupply,
        long deposits,
        long cash,
        long treasuries,
        long taxCollected,
        long taxArrears,
        double averageTaxRate,
        double averageSatisfaction,
        long rejectedActions,
        long events) {

    public static final List<String> COLUMNS = List.of(
            "day", "agents", "polities", "members", "unaffiliated", "homeless_members", "chunks", "outposts",
            "plots", "private_plots", "embassies", "money_supply", "deposits", "cash", "treasuries",
            "tax_collected", "tax_arrears", "avg_tax_rate", "avg_satisfaction", "rejected_actions", "events");

    /** Die Werte als CSV-Zeile ohne Zeilenende, Kommazahlen mit Punkt und vier Nachkommastellen. */
    public String toCsvRow() {
        return String.join(",",
                Long.toString(day), Integer.toString(agents), Integer.toString(polities), Integer.toString(members),
                Integer.toString(unaffiliated), Integer.toString(homelessMembers), Integer.toString(chunks),
                Integer.toString(outposts), Integer.toString(plots), Integer.toString(privatePlots),
                Integer.toString(embassies), Long.toString(moneySupply), Long.toString(deposits), Long.toString(cash),
                Long.toString(treasuries), Long.toString(taxCollected), Long.toString(taxArrears),
                decimal(averageTaxRate), decimal(averageSatisfaction), Long.toString(rejectedActions),
                Long.toString(events));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
