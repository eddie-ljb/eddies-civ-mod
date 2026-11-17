package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Der Betrieb einer Firma.
 *
 * @param invested    insgesamt in die Ausstattung investiert (kleinste Währungseinheit)
 * @param autoSell    ob die Tageserzeugung automatisch an den Servermarkt geht
 * @param stock       Lagerbestand je Ware
 * @param lastDay     Tag der letzten Produktion
 * @param lastOutput  Stückzahl der letzten Tagesproduktion
 * @param lastRevenue Erlös des letzten automatischen Verkaufs
 */
public record Enterprise(PolityId company, Branch branch, long invested, boolean autoSell,
                         Map<Commodity, Long> stock, Optional<Day> lastDay, long lastOutput, long lastRevenue) {

    /** Grenze für eine einzelne Ware im Lager: zwanzig volle Truhen. */
    public static final long STOCK_LIMIT = 20L * 27 * 64;
    /** Kosten der ersten Ausstattungsstufe: 20 Smaragde; jede weitere kostet doppelt so viel. */
    public static final long FIRST_LEVEL_COST = 2_000;
    public static final int MAX_LEVEL = 8;

    public Enterprise {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(lastDay, "lastDay");
        if (invested < 0) {
            throw new IllegalArgumentException("Negative Investition");
        }
        stock = Map.copyOf(new TreeMap<>(stock));
    }

    public static Enterprise start(PolityId company, Branch branch) {
        return new Enterprise(company, branch, 0, true, Map.of(), Optional.empty(), 0, 0);
    }

    /** Ausstattungsstufe aus der Investitionssumme: 20, 60, 140, 300 … Smaragde für Stufe 1, 2, 3, 4 … */
    public int level() {
        int level = 0;
        long needed = FIRST_LEVEL_COST;
        long total = needed;
        while (level < MAX_LEVEL && invested >= total) {
            level++;
            needed *= 2;
            total += needed;
        }
        return level;
    }

    /** Was bis zur nächsten Stufe noch fehlt; 0 auf der höchsten Stufe. */
    public long toNextLevel() {
        if (level() >= MAX_LEVEL) {
            return 0;
        }
        long needed = FIRST_LEVEL_COST;
        long total = needed;
        for (int i = 0; i < level(); i++) {
            needed *= 2;
            total += needed;
        }
        return total - invested;
    }

    /** Faktor der Ausstattung: +25 % je Stufe. */
    public double equipmentFactor() {
        return 1 + 0.25 * level();
    }

    /**
     * Arbeitsleistung aus Beschäftigten: abnehmender Grenzertrag ({@code n^0,85}), damit Einstellen lohnt, aber
     * nicht beliebig.
     */
    public static double labour(double workers) {
        return workers <= 0 ? 0 : Math.pow(workers, 0.85);
    }

    /** Tagesproduktion je Ware bei dieser Arbeitsleistung. */
    public Map<Commodity, Long> dailyOutput(double workers) {
        double factor = labour(workers) * equipmentFactor();
        Map<Commodity, Long> output = new TreeMap<>();
        for (Branch.Yield yield : branch.yields()) {
            long quantity = Math.round(yield.perWorker() * factor);
            if (quantity > 0) {
                output.put(yield.asCommodity(), quantity);
            }
        }
        return output;
    }

    public long stockOf(Commodity commodity) {
        return stock.getOrDefault(commodity, 0L);
    }

    Enterprise withStock(Map<Commodity, Long> next) {
        return new Enterprise(company, branch, invested, autoSell, next, lastDay, lastOutput, lastRevenue);
    }

    Enterprise invested(long more) {
        return new Enterprise(company, branch, invested + more, autoSell, stock, lastDay, lastOutput, lastRevenue);
    }

    Enterprise selling(boolean auto) {
        return new Enterprise(company, branch, invested, auto, stock, lastDay, lastOutput, lastRevenue);
    }

    Enterprise switchedTo(Branch next) {
        return new Enterprise(company, next, invested / 2, autoSell, stock, lastDay, lastOutput, lastRevenue);
    }

    Enterprise produced(Map<Commodity, Long> next, Day day, long output, long revenue) {
        return new Enterprise(company, branch, invested, autoSell, next, Optional.of(day), output, revenue);
    }
}
