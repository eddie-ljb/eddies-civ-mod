package de.etiennebader.civ.server.company;

import java.util.List;

/**
 * Eine notierte Firma für Menüs und Webseite. Beträge als Text („1,25“), Prozent gerundet auf eine Stelle.
 *
 * @param units Kurs in Einheiten, zum Sortieren
 */
public record ListingView(String id, String name, String head, String price, long units, double change,
                          String bookValue, String marketCap, String treasury, long shares, double dividendYield,
                          long volumeToday, List<QuotePoint> quotes, List<HolderLine> holders, int employees,
                          long freeFloat) {

    /** Ein Tageskurs in Einheiten. */
    public record QuotePoint(long day, long open, long high, long low, long close, long volume) {
    }

    /** Ein Aktionär. */
    public record HolderLine(String name, long shares, double percent) {
    }
}
