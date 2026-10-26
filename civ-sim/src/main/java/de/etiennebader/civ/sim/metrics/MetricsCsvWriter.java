package de.etiennebader.civ.sim.metrics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Schreibt Tageskennzahlen als CSV mit Kopfzeile. Zeilenende ist immer {@code \n}, damit die
 * Datei auf jedem Betriebssystem byte-gleich ist.
 */
public final class MetricsCsvWriter implements Consumer<DailyMetrics> {

    private final Writer writer;

    public MetricsCsvWriter(Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
        writeLine(String.join(",", DailyMetrics.COLUMNS));
    }

    @Override
    public void accept(DailyMetrics metrics) {
        writeLine(metrics.toCsvRow());
    }

    private void writeLine(String line) {
        try {
            writer.write(line);
            writer.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
