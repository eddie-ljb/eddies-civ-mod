package de.etiennebader.civ.sim;

/**
 * Wie oft und wie lange gespeichert wurde. Nicht Teil der Kennzahlen-CSV, weil Laufzeiten
 * nicht reproduzierbar sind.
 *
 * @param saves      Anzahl Speichervorgänge
 * @param totalNanos Gesamtdauer aller Speichervorgänge
 * @param events     Anzahl ins Audit-Log geschriebener Events
 */
public record SaveStatistics(int saves, long totalNanos, long events) {

    public static final SaveStatistics NONE = new SaveStatistics(0, 0, 0);

    SaveStatistics plus(long nanos, long writtenEvents) {
        return new SaveStatistics(saves + 1, totalNanos + nanos, events + writtenEvents);
    }

    public long totalMillis() {
        return totalNanos / 1_000_000;
    }
}
